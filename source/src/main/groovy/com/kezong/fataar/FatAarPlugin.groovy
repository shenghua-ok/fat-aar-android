package com.kezong.fataar

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.LibraryVariant
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.provider.MapProperty

/**
 * plugin entry
 */
class FatAarPlugin implements Plugin<Project> {

    public static final String ARTIFACT_TYPE_AAR = 'aar'

    public static final String ARTIFACT_TYPE_JAR = 'jar'

    private static final String CONFIG_NAME = "embed"

    public static final String CONFIG_SUFFIX = 'Embed'

    private Project project
    private final Collection<Configuration> embedConfigurations = new ArrayList<>()

    private def variantDataMap = [:]  // key: variantName, value: map with artifacts, dependencies
    private final Map<String, Map<String, String>> libraryPackageMap = new HashMap<>()

    @Override
    void apply(Project project) {
        libraryPackageMap.clear()
        this.project = project
        checkAndroidPlugin()
        FatUtils.attach(project)
        DirectoryManager.attach(project)
        project.extensions.create(FatAarExtension.NAME, FatAarExtension)
        createConfigurations()
        MapProperty<String, String> mapProp = project.objects.mapProperty(String, String)
        initRClassesHandler(mapProp)
        project.afterEvaluate {
            initTransitive()
            findDependency(mapProp)
        }
    }

    private void initRClassesHandler(MapProperty<String, String> mapProperty) {
        project.plugins.withId("com.android.library") {
            def androidComponents = project.extensions.findByType(AndroidComponentsExtension)
            if (androidComponents != null) {
                androidComponents.onVariants(androidComponents.selector().all(), new Action<LibraryVariant>() {
                    @Override
                    void execute(LibraryVariant variant) {
                        variantDataMap[variant.name] = [
                                variant: variant
                        ]
                        registerRClassesHandler(variant, mapProperty)
                    }
                })
            } else {
                println("AndroidComponentsExtension not found.")
            }
        }
    }

    private static registerRClassesHandler(LibraryVariant mVariant, MapProperty<String, String> mapProperty) {
        mVariant.instrumentation.transformClassesWith(RClassesVisitorFactory.class, InstrumentationScope.PROJECT) { params ->
            params.transformTable.set(mapProperty)
        }
        mVariant.instrumentation.setAsmFramesComputationMode(
                FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_CLASSES
        )
    }

    private void initTransitive() {
        embedConfigurations.each {
            if (project.fataar.transitive) {
                it.transitive = true
            }
        }
    }

    private void findDependency(MapProperty<String, String> mapProperty) {
        variantDataMap.each { _, data ->
            def variant = data.variant

            Collection<ResolvedArtifact> artifacts = new ArrayList()
            Collection<ResolvedDependency> firstLevelDependencies = new ArrayList<>()
            embedConfigurations.each { configuration ->
                if (configuration.name == CONFIG_NAME
                        || configuration.name == variant.buildType + CONFIG_SUFFIX
                        || configuration.name == variant.flavorName + CONFIG_SUFFIX
                        || configuration.name == variant.name + CONFIG_SUFFIX) {
                    Collection<ResolvedArtifact> resolvedArtifacts = resolveArtifacts(configuration)
                    artifacts.addAll(resolvedArtifacts)
                    artifacts.addAll(dealUnResolveArtifacts(configuration, variant as LibraryVariant, resolvedArtifacts))
                    firstLevelDependencies.addAll(configuration.resolvedConfiguration.firstLevelModuleDependencies)
                }
            }
            def processor = new VariantProcessor(project, variant)
            //firstLevelDependencies.size >= artifacts.size
            processor.processVariant(artifacts, firstLevelDependencies, new IForRClasses() {
                @Override
                void setPackage(String variantNameArg, Map<String, String> libPkgNameMap) {
                    mapProperty.set(libPkgNameMap)
                    //libraryPackageMap.put(variantNameArg, libPkgNameMap)
                }
            })
        }
    }

    private void createConfigurations() {
        //so this value should be 'embed'
        Configuration embedConf = project.configurations.create(CONFIG_NAME)
        createConfiguration(embedConf)
        FatUtils.logInfo("Creating configuration embed")

        project.android.buildTypes.all { buildType ->
            //buildType is either debug or release.
            //so this value should be 'debugEmbed' or 'releaseEmbed'
            String configName = buildType.name + CONFIG_SUFFIX
            Configuration configuration = project.configurations.create(configName)
            createConfiguration(configuration)
            FatUtils.logInfo("Creating configuration " + configName)
        }

        project.android.productFlavors.all { flavor ->
            //so this value should be 'flavorEmbed'
            String configName = flavor.name + CONFIG_SUFFIX
            Configuration configuration = project.configurations.create(configName)
            createConfiguration(configuration)
            FatUtils.logInfo("Creating configuration " + configName)
            project.android.buildTypes.all { buildType ->
                String variantName = flavor.name + buildType.name.capitalize()
                //so this value should be 'flavorDebugEmbed' or 'flavorReleaseEmbed'
                String variantConfigName = variantName + CONFIG_SUFFIX
                Configuration variantConfiguration = project.configurations.create(variantConfigName)
                createConfiguration(variantConfiguration)
                FatUtils.logInfo("Creating configuration " + variantConfigName)
            }
        }
    }

    private void checkAndroidPlugin() {
        if (!project.plugins.hasPlugin('com.android.library')) {
            throw new ProjectConfigurationException('fat-aar-plugin must be applied in project that' +
                    ' has android library plugin!', null)
        }
    }

    private void createConfiguration(Configuration embedConf) {
        embedConf.visible = false
        embedConf.transitive = false
        project.gradle.addListener(new EmbedResolutionListener(project, embedConf))
        embedConfigurations.add(embedConf)
    }

    private Collection<ResolvedArtifact> resolveArtifacts(Configuration configuration) {
        def set = new ArrayList()
        if (configuration != null) {
            configuration.resolvedConfiguration.resolvedArtifacts.each { artifact ->
                if (ARTIFACT_TYPE_AAR == artifact.type || ARTIFACT_TYPE_JAR == artifact.type) {
                    //
                } else {
                    throw new ProjectConfigurationException('Only support embed aar and jar dependencies!', null)
                }
//                FatUtils.logInLoop("[resolvedFile][$artifact.type]${artifact.moduleVersion.id}###[${FatUtils.formatDataSize(artifact.file.size())}]")
                set.add(artifact)
            }
        }
        return set
    }

    private Collection<ResolvedArtifact> dealUnResolveArtifacts(Configuration configuration, LibraryVariant variant, Collection<ResolvedArtifact> artifacts) {
        def artifactList = new ArrayList()
        configuration.resolvedConfiguration.firstLevelModuleDependencies.each { dependency ->
//            [lib-java][lib-aar][lib-aar2][guava][fresco][glide]
            def match = artifacts.any { artifact ->
//            [lib-java][guava][fresco][glide]
                dependency.moduleName == artifact.moduleVersion.id.name
            }

            if (!match) {
                def flavorArtifact = FlavorArtifact.createFlavorArtifact(project, variant, dependency)
                if (flavorArtifact != null) {
//                    FatUtils.logInLoop("[unresolvedFile][$flavorArtifact.type]${flavorArtifact.moduleVersion.id}###[${FatUtils.formatDataSize(flavorArtifact.file.size())}]")
                    artifactList.add(flavorArtifact)
                }
            }
        }
        return artifactList
    }
}
