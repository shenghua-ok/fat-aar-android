package com.kezong.fataar

import com.android.build.api.variant.LibraryVariant
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import groovy.json.JsonOutput
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.ResolvableDependency
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Core
 * Processor for variant
 */
class VariantProcessor {

    private final Project mProject

    private final LibraryVariant mVariant

    private Collection<AndroidArchiveLibrary> mAndroidArchiveLibraries = new ArrayList<>()

    private Collection<File> mJarFiles = new ArrayList<>()

    private Collection<Task> mExplodeTasks = new ArrayList<>()

    private VersionAdapter mVersionAdapter

    private TaskProvider mMergeClassTask

    VariantProcessor(Project project, LibraryVariant variant) {
        mProject = project
        mVariant = variant
        mVersionAdapter = new VersionAdapter(project, variant)
    }

    void addAndroidArchiveLibrary(AndroidArchiveLibrary library) {
        mAndroidArchiveLibraries.add(library)
    }

    void addJarFile(File jar) {
        mJarFiles.add(jar)
    }

    void processVariant(Collection<ResolvedArtifact> artifacts,
                        Collection<ResolvableDependency> dependencies) {
        String taskPath = 'pre' + mVariant.name.capitalize() + 'Build'
        TaskProvider prepareTask = mProject.tasks.named(taskPath)
        if (prepareTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        TaskProvider bundleTask = VersionAdapter.getBundleTaskProvider(mProject, mVariant.name)
        preEmbed(artifacts, dependencies, prepareTask)
        processArtifacts(artifacts, prepareTask, bundleTask)
        processClassesAndJars(bundleTask)
        if (mAndroidArchiveLibraries.isEmpty()) {
            return
        }
        processManifest()
        processResources()
        processAssets()
        processJniLibs()
        processConsumerProguard()
        processGenerateProguard()
        processDataBinding(bundleTask)
        processRClasses(bundleTask)
        processCompatibleWith8()
    }

    private static void printEmbedArtifacts(Collection<ResolvedArtifact> artifacts,
                                            Collection<ResolvedDependency> dependencies) {
        Collection<String> moduleNames = artifacts.stream().map { it.moduleVersion.id.name }.collect()
        dependencies.each { dependency ->
            if (!moduleNames.contains(dependency.moduleName)) {
                return
            }

            ResolvedArtifact self = dependency.allModuleArtifacts.find { module ->
                module.moduleVersion.id.name == dependency.moduleName
            }

            if (self == null) {
                return
            }

            FatUtils.logAnytime("[embed detected][$self.type]${self.moduleVersion.id}###[${FatUtils.formatDataSize(self.file.size())}]")
            moduleNames.remove(self.moduleVersion.id.name)

            dependency.allModuleArtifacts.each { artifact ->
                if (!moduleNames.contains(artifact.moduleVersion.id.name)) {
                    return
                }
                if (artifact != self) {
                    FatUtils.logAnytime("    - [embed detected][transitive][$artifact.type]${artifact.moduleVersion.id}###[${FatUtils.formatDataSize(artifact.file.size())}]")
                    moduleNames.remove(artifact.moduleVersion.id.name)
                }
            }
        }

        moduleNames.each { name ->
            ResolvedArtifact artifact = artifacts.find { it.moduleVersion.id.name == name }
            if (artifact != null) {
                FatUtils.logAnytime("[embed detected][unresovled][$artifact.type]${artifact.moduleVersion.id}###[${FatUtils.formatDataSize(artifact.file.size())}]")
            }
        }
    }

    private void preEmbed(Collection<ResolvedArtifact> artifacts,
                          Collection<ResolvedDependency> dependencies,
                          TaskProvider prepareTask) {
        TaskProvider embedTask = mProject.tasks.register("pre${mVariant.name.capitalize()}Embed") {
            doFirst {
                printEmbedArtifacts(artifacts, dependencies)
            }
        }

        prepareTask.configure {
            dependsOn embedTask
        }
    }

    private TaskProvider configureReBundleAarTask(TaskProvider bundleTask) {
        File aarOutputFile
        File reBundleDir = DirectoryManager.getReBundleDirectory(mVariant)
        bundleTask.configure { it ->
            if (FatUtils.compareVersion(mProject.gradle.gradleVersion, "5.1") >= 0) {
                aarOutputFile = new File(it.getDestinationDirectory().getAsFile().get(), it.getArchiveFileName().get())
            } else {
                aarOutputFile = new File(it.destinationDir, it.archiveName)
            }

            doFirst {
                // Delete previously unzipped data.
                reBundleDir.deleteDir()
            }

            doLast {
                mProject.copy {
                    from mProject.zipTree(aarOutputFile)
                    into reBundleDir
                }
                FatUtils.deleteEmptyDir(reBundleDir)
            }
        }

        String taskName = "reBundleAar${mVariant.name.capitalize()}"
        TaskProvider task = mProject.getTasks().register(taskName, Zip.class) {
            it.from reBundleDir
            it.include "**"
            if (aarOutputFile == null) {
                aarOutputFile = mVersionAdapter.getOutputFile()
            }
            if (FatUtils.compareVersion(mProject.gradle.gradleVersion, "5.1") >= 0) {
                it.getArchiveFileName().set(aarOutputFile.getName())
                it.getDestinationDirectory().set(aarOutputFile.getParentFile())
            } else {
                it.archiveName = aarOutputFile.getName()
                it.destinationDir = aarOutputFile.getParentFile()
            }

            doLast {
                FatUtils.logAnytime(" target: ${aarOutputFile.absolutePath} [${FatUtils.formatDataSize(aarOutputFile.size())}]")
            }
        }

        return task
    }

    private void processRClasses(TaskProvider<Task> bundleTask) {
        TaskProvider reBundleTask = configureReBundleAarTask(bundleTask)
        TaskProvider transformTask = mProject.tasks.named("transform${mVariant.name.capitalize()}ClassesWithAsm")
        if (mProject.fataar.transformR) {
            transformRClasses(transformTask, bundleTask, reBundleTask)
        } else {
            generateRClasses(transformTask, bundleTask, reBundleTask)
        }
    }

    private void transformRClasses(TaskProvider transformTask, TaskProvider bundleTask, TaskProvider reBundleTask) {
        TaskProvider mapJsonTask = mProject.tasks.register("gerenerate${mVariant.name.capitalize()}RMapJson") {
            def mappingFile = VersionAdapter.getRMappingJsonProvider(mProject).get().asFile
            outputs.file(mappingFile)
            doFirst {
                Collection libraryPackages = mAndroidArchiveLibraries
                        .stream()
                        .map { it.packageName }
                        .collect()
                def map = buildTransformTable(mVariant.namespace.get(), libraryPackages)
                try {
                    mappingFile.parentFile.mkdirs()
                    mappingFile.text = JsonOutput.prettyPrint(JsonOutput.toJson(map))
                    FatUtils.logAnytime("Mapping file created at: ${mappingFile.absolutePath}")
                } catch (Exception e) {
                    FatUtils.logAnytime("Write file failed: ${e.message}")
                    throw e
                }
            }
        }
        mapJsonTask.configure {
            dependsOn(mMergeClassTask)
        }
        transformTask.configure {
            it.dependsOn(mapJsonTask)
        }
        bundleTask.configure {
            finalizedBy(reBundleTask)
        }
    }

    private static Map<String, String> buildTransformTable(String targetPackage, Collection<String> libraryPackages) {
        if (targetPackage == null || libraryPackages == null) {
            return null;
        }

        final List<String> resourceTypes = Arrays.asList("anim", "animator", "array", "attr", "bool", "color", "dimen",
                "drawable", "font", "fraction", "id", "integer", "interpolator", "layout", "menu", "mipmap", "navigation",
                "plurals", "raw", "string", "style", "styleable", "transition", "xml");

        HashMap<String, String> map = new HashMap<>();
        for (String resource : resourceTypes) {
            String targetClass = targetPackage.replace(".", "/") + "/R\$${resource}";
            for (String libraryPackage : libraryPackages) {
                String fromClass = libraryPackage.replace(".", "/") + "/R\$${resource}";
                map.put(fromClass, targetClass);
            }
        }

        return map;
    }

    private void generateRClasses(TaskProvider transformTask, TaskProvider<Task> bundleTask, TaskProvider<Task> reBundleTask) {
        transformTask.configure {
            dependsOn(mMergeClassTask)
        }
        RClassesGenerate rClassesGenerate = new RClassesGenerate(mProject, mVariant, mAndroidArchiveLibraries)
        TaskProvider RTask = rClassesGenerate.configure(reBundleTask)
        bundleTask.configure {
            finalizedBy(RTask)
        }
    }

    /**
     * copy data binding file must be do last in BundleTask, and reBundleTask will be package it.
     * @param bundleTask
     */
    private void processDataBinding(TaskProvider<Task> bundleTask) {
        bundleTask.configure {
            doLast {
                for (archiveLibrary in mAndroidArchiveLibraries) {
                    if (archiveLibrary.dataBindingFolder != null && archiveLibrary.dataBindingFolder.exists()) {
                        String filePath = "${DirectoryManager.getReBundleDirectory(mVariant).path}/${archiveLibrary.dataBindingFolder.name}"
                        new File(filePath).mkdirs()
                        mProject.copy {
                            from archiveLibrary.dataBindingFolder
                            into filePath
                        }
                    }

                    if (archiveLibrary.dataBindingLogFolder != null && archiveLibrary.dataBindingLogFolder.exists()) {
                        String filePath = "${DirectoryManager.getReBundleDirectory(mVariant).path}/${archiveLibrary.dataBindingLogFolder.name}"
                        new File(filePath).mkdirs()
                        mProject.copy {
                            from archiveLibrary.dataBindingLogFolder
                            into filePath
                        }
                    }
                }
            }
        }
    }

    // gradle < 6, return TaskDependency
    // gradle >= 6, return TaskDependencyContainer
    static Set<Task> getTaskDependency(ResolvedArtifact artifact) {
        def taskDep = null
        try {
            if (artifact.metaClass.hasProperty(artifact, 'buildDependencies')) {
                taskDep = artifact.buildDependencies
            } else if (artifact.metaClass.hasProperty(artifact, 'builtBy')) {
                taskDep = artifact.builtBy
            } else if (artifact.metaClass.hasProperty(artifact, 'id')) {
                def artiId = artifact.getId()
                if (artiId instanceof PublishArtifactLocalArtifactMetadata) {
                    def metadata = (PublishArtifactLocalArtifactMetadata) artiId
                    taskDep = metadata.buildDependencies
                }
            }
        } catch (Exception ignore) {
            ignore.printStackTrace()
        }

        if (taskDep instanceof TaskDependency) {

            try {
                return taskDep.getDependencies(null)
            } catch (Exception e) {
                e.printStackTrace()
            }
        }

        if (taskDep != null) {

            try {
                def context = new CachingTaskDependencyResolveContext()
                taskDep.visitDependencies(context)
                if (!context.queue.isEmpty()) {
                    return context.queue.getFirst().getDependencies()
                } else {
                    return new HashSet<>()
                }
            } catch (Exception ignored) {
                ignored.printStackTrace()
            }
        }
        return new HashSet<>()
    }

    /**
     * exploded artifact files
     */
    private void processArtifacts(Collection<ResolvedArtifact> artifacts, TaskProvider<Task> prepareTask, TaskProvider<Task> bundleTask) {
        if (artifacts == null) {
            return
        }
        for (final ResolvedArtifact artifact in artifacts) {
            if (FatAarPlugin.ARTIFACT_TYPE_JAR == artifact.type) {
                addJarFile(artifact.file)
            } else if (FatAarPlugin.ARTIFACT_TYPE_AAR == artifact.type) {
                AndroidArchiveLibrary archiveLibrary = new AndroidArchiveLibrary(mProject, artifact, mVariant.name)
                addAndroidArchiveLibrary(archiveLibrary)
                Set<Task> dependencies = getTaskDependency(artifact)
                final def zipFolder = archiveLibrary.getRootFolder()
                zipFolder.mkdirs()
                def group = artifact.getModuleVersion().id.group.capitalize()
                def name = artifact.name.capitalize()
                String taskName = "explode${group}${name}${mVariant.name.capitalize()}"
                if (!mProject.tasks.names.contains(taskName)) {
                    Task explodeTask = mProject.tasks.register(taskName, Copy) {
                        from mProject.zipTree(artifact.file.absolutePath)
                        into zipFolder

                        doFirst {
                            // Delete previously extracted data.
                            zipFolder.deleteDir()
                        }
                    }.get()

                    if (dependencies.size() == 0) {
                        explodeTask.dependsOn(prepareTask)
                    } else {
                        explodeTask.dependsOn(dependencies.first())
                    }
                    Task javacTask = mVersionAdapter.getJavaCompileTask()
                    javacTask.dependsOn(explodeTask)
                    bundleTask.configure {
                        dependsOn(explodeTask)
                    }
                    mExplodeTasks.add(explodeTask)
                }
            }
        }
    }

    /**
     * merge manifest
     */
    private void processManifest() {
        TaskProvider<LibraryManifestMerger> manifestsMergeTask = mProject.tasks.register("merge${mVariant.name.capitalize()}Manifest", LibraryManifestMerger) {
        }
        def manifestTaskProvider = mProject.tasks.named("process${mVariant.name.capitalize()}Manifest")
        manifestTaskProvider.configure { processManifestTask ->
            File manifestOutput
            try {
                //8.0.0+
                manifestOutput = processManifestTask.manifestOutputFile.get().asFile
            } catch (MissingPropertyException | MissingMethodException e) {
                if (FatUtils.compareVersion(VersionAdapter.AGPVersion, "4.2.0-alpha07") >= 0) {
                    manifestOutput = mProject.file("${mProject.buildDir.path}/intermediates/merged_manifest/${mVariant.name}/AndroidManifest.xml")
                } else if (FatUtils.compareVersion(VersionAdapter.AGPVersion, "3.3.0") >= 0) {
                    manifestOutput = mProject.file("${mProject.buildDir.path}/intermediates/library_manifest/${mVariant.name}/AndroidManifest.xml")
                } else {
                    manifestOutput = mProject.file(processManifestTask.getManifestOutputDirectory().absolutePath + "/AndroidManifest.xml")
                }
            }

            final List<File> inputManifests = new ArrayList<>()
            for (archiveLibrary in mAndroidArchiveLibraries) {
                inputManifests.add(archiveLibrary.getManifest())
            }

            processManifestTask.dependsOn(mExplodeTasks)
            processManifestTask.inputs.files(inputManifests)
            processManifestTask.doLast {
                // Merge manifests
                manifestsMergeTask.get().setGradleVersion(mProject.getGradle().getGradleVersion())
                manifestsMergeTask.get().setGradlePluginVersion(VersionAdapter.AGPVersion)
                manifestsMergeTask.get().setMainManifestFile(manifestOutput)
                manifestsMergeTask.get().setSecondaryManifestFiles(inputManifests)
                manifestsMergeTask.get().setOutputFile(manifestOutput)
                manifestsMergeTask.get().doTaskAction()
            }
        }
    }

    private TaskProvider handleClassesMergeTask(final boolean isMinifyEnabled) {
        final TaskProvider task = mProject.tasks.register("mergeClasses" + mVariant.name.capitalize()) {
            dependsOn(mExplodeTasks)
            dependsOn(mVersionAdapter.getJavaCompileTask())
            try {
                // main lib maybe not use kotlin
                TaskProvider kotlinCompile = mProject.tasks.named("compile${mVariant.name.capitalize()}Kotlin")
                if (kotlinCompile != null) {
                    dependsOn(kotlinCompile)
                }
            } catch (Exception ignore) {

            }

            inputs.files(mAndroidArchiveLibraries.stream().map { it.classesJarFile }.collect())
                    .withPathSensitivity(PathSensitivity.RELATIVE)
            if (isMinifyEnabled) {
                inputs.files(mAndroidArchiveLibraries.stream().map { it.localJars }.collect())
                        .withPathSensitivity(PathSensitivity.RELATIVE)
                inputs.files(mJarFiles).withPathSensitivity(PathSensitivity.RELATIVE)
            }
            File outputDir = DirectoryManager.getMergeClassDirectory(mVariant)
            File javacDir = mVersionAdapter.getClassPathDirFiles().first()
            outputs.dir(outputDir)

            doFirst {
                // Extract relative paths and delete previous output.
                def pathsToDelete = new ArrayList<Path>()
                mProject.fileTree(outputDir).forEach {
                    pathsToDelete.add(Paths.get(outputDir.absolutePath).relativize(Paths.get(it.absolutePath)))
                }
                outputDir.deleteDir()
                // Delete output files from javac dir.
                pathsToDelete.forEach {
                    Files.deleteIfExists(Paths.get("$javacDir.absolutePath/${it.toString()}"))
                }
            }

            doLast {
                ExplodedHelper.processClassesJarInfoClasses(mProject, mAndroidArchiveLibraries, outputDir)
                if (isMinifyEnabled) {
                    ExplodedHelper.processLibsIntoClasses(mProject, mAndroidArchiveLibraries, mJarFiles, outputDir)
                }

                mProject.copy {
                    from outputDir
                    into javacDir
                    exclude 'META-INF/'
                }

                mProject.copy {
                    from outputDir.absolutePath + "/META-INF"
                    into DirectoryManager.getKotlinMetaDirectory(mVariant)
                    include '*.kotlin_module'
                }
            }
        }
        return task
    }

    private TaskProvider handleJarMergeTask(final TaskProvider syncLibTask) {
        final TaskProvider task = mProject.tasks.register("mergeJars" + mVariant.name.capitalize()) {
            dependsOn(mExplodeTasks)
            dependsOn(mVersionAdapter.getJavaCompileTask())
            mustRunAfter(syncLibTask)

            inputs.files(mAndroidArchiveLibraries.stream().map { it.libsFolder }.collect())
                    .withPathSensitivity(PathSensitivity.RELATIVE)
            inputs.files(mJarFiles).withPathSensitivity(PathSensitivity.RELATIVE)
            def outputDir = mVersionAdapter.getLibsDirFile()
            outputs.dir(outputDir)

            doFirst {
                ExplodedHelper.processLibsIntoLibs(mProject, mAndroidArchiveLibraries, mJarFiles, outputDir)
            }
        }
        return task
    }

    /**
     * merge classes and jars
     */
    private void processClassesAndJars(TaskProvider<Task> bundleTask) {
        def isMinifyEnabled = mProject.android.buildTypes.findByName(mVariant.buildType)?.minifyEnabled ?: false

        TaskProvider syncLibTask = mProject.tasks.named(mVersionAdapter.getSyncLibJarsTaskPath())
        TaskProvider extractAnnotationsTask = mProject.tasks.named("extract${mVariant.name.capitalize()}Annotations")

        mMergeClassTask = handleClassesMergeTask(isMinifyEnabled)
        syncLibTask.configure {
            dependsOn(mMergeClassTask)
            inputs.files(mAndroidArchiveLibraries.stream().map { it.libsFolder }.collect())
                    .withPathSensitivity(PathSensitivity.RELATIVE)
            inputs.files(mJarFiles).withPathSensitivity(PathSensitivity.RELATIVE)
        }
        extractAnnotationsTask.configure {
            mustRunAfter(mMergeClassTask)
        }

        if (!isMinifyEnabled) {
            TaskProvider mergeJars = handleJarMergeTask(syncLibTask)
            bundleTask.configure {
                dependsOn(mergeJars)
            }
        }
    }

    /**
     * merge R.txt (actually is to fix issue caused by provided configuration) and res
     *
     * Now the same res Id will cause a build exception: Duplicate resources, to encourage you to change res Id.
     * Adding "android.disableResourceValidation=true" to "gradle.properties" can do a trick to skip the exception, but is not recommended.
     */
    private void processResources() {
        String taskPath = "generate" + mVariant.name.capitalize() + "Resources"
        TaskProvider resourceGenTask = mProject.tasks.named(taskPath)
        if (resourceGenTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        resourceGenTask.configure {
            dependsOn(mExplodeTasks)
        }
        mProject.android.sourceSets.each { DefaultAndroidSourceSet sourceSet ->
            if (sourceSet.name == mVariant.name) {
                for (archiveLibrary in mAndroidArchiveLibraries) {
                    FatUtils.logInfo("Merge resource，Library res：${archiveLibrary.resFolder}")
                    sourceSet.res.srcDir(archiveLibrary.resFolder)
                }
            }
        }
    }

    /**
     * merge assets
     *
     * AaptOptions.setIgnoreAssets and AaptOptions.setIgnoreAssetsPattern will work as normal
     */
    private void processAssets() {
        String mergeAssetsTaskName = "package${mVariant.name.capitalize()}Assets"
        TaskProvider mergeAssetsTask = mProject.tasks.named(mergeAssetsTaskName)
        if (mergeAssetsTask == null) {
            throw new RuntimeException("Can not find task in variant.getMergeAssets()!")
        }
        mergeAssetsTask.configure {
            dependsOn(mExplodeTasks)
            doFirst {
                mProject.android.sourceSets.each {
                    if (it.name == mVariant.name) {
                        for (archiveLibrary in mAndroidArchiveLibraries) {
                            if (archiveLibrary.assetsFolder != null && archiveLibrary.assetsFolder.exists()) {
                                FatUtils.logInfo("Merge assets，Library assets folder：${archiveLibrary.assetsFolder}")
                                it.assets.srcDir(archiveLibrary.assetsFolder)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * merge jniLibs
     */
    private void processJniLibs() {
        String taskPath = 'merge' + mVariant.name.capitalize() + 'JniLibFolders'
        TaskProvider mergeJniLibsTask = mProject.tasks.named(taskPath)
        if (mergeJniLibsTask == null) {
            throw new RuntimeException("Can not find task ${taskPath}!")
        }
        mergeJniLibsTask.configure {
            dependsOn(mExplodeTasks)
            doFirst {
                mProject.android.sourceSets.each {
                    if (it.name == mVariant.name) {
                        for (archiveLibrary in mAndroidArchiveLibraries) {
                            if (archiveLibrary.jniFolder != null && archiveLibrary.jniFolder.exists()) {
                                FatUtils.logInfo("Merge resource，Library jniLibs：${archiveLibrary.jniFolder}")
                                it.jniLibs.srcDir(archiveLibrary.jniFolder)
                            }
                        }
                    }
                }
            }
        }

    }

    /**
     * merge proguard.txt
     */
    private void processConsumerProguard() {
        String mergeTaskName = 'merge' + mVariant.name.capitalize() + 'ConsumerProguardFiles'
        TaskProvider mergeFileTask = mProject.tasks.named(mergeTaskName)
        if (mergeFileTask == null) {
            throw new RuntimeException("Can not find task ${mergeTaskName}!")
        }

        mergeFileTask.configure {
            dependsOn(mExplodeTasks)
            doLast {
                try {
                    Collection<File> files = mAndroidArchiveLibraries.stream().map { it.proguardRules }.collect()
                    File of
                    if (outputFile instanceof File) {
                        of = outputFile
                    } else {
                        // RegularFileProperty.class
                        of = outputFile.get().asFile
                    }
                    FatUtils.mergeFiles(files, of)
                } catch (Exception e) {
                    FatUtils.logAnytime(("If you see this error message, please submit issue to " +
                            "https://github.com/kezong/fat-aar-android/issues with version of AGP and Gradle. Thank you.")
                    )
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * merge consumer proguard to generate proguard
     * @since AGP 3.6
     */
    private void processGenerateProguard() {
        TaskProvider mergeGenerateProguardTask
        try {
            String mergeName = 'merge' + mVariant.name.capitalize() + 'GeneratedProguardFiles'
            mergeGenerateProguardTask = mProject.tasks.named(mergeName)
        } catch (Exception ignore) {
            return
        }

        mergeGenerateProguardTask.configure {
            dependsOn(mExplodeTasks)
            doLast {
                try {
                    Collection<File> files = mAndroidArchiveLibraries.stream().map { it.proguardRules }.collect()
                    File of
                    if (outputFile instanceof File) {
                        of = outputFile
                    } else {
                        // RegularFileProperty.class
                        of = outputFile.get().asFile
                    }
                    FatUtils.mergeFiles(files, of)
                } catch (Exception e) {
                    FatUtils.logAnytime(("If you see this error message, please submit issue to " +
                            "https://github.com/kezong/fat-aar-android/issues with version of AGP and Gradle. Thank you.")
                    )
                    e.printStackTrace()
                }
            }
        }
    }


    private void processCompatibleWith8() {
        processMapSourceSetPathsTask()
        processVerifyReleaseSources()
        processGenSafeArgsTask()
    }

    private void processMapSourceSetPathsTask() {
        String taskName = "map${mVariant.name.capitalize()}SourceSetPaths"
        if (!mProject.tasks.names.contains(taskName)) {
            return
        }
        TaskProvider mapSourceSetPathsTask = mProject.tasks.named(taskName)
        mapSourceSetPathsTask.configure {
            dependsOn(mExplodeTasks)
        }
    }

    private void processVerifyReleaseSources() {
        String taskName = "verify${mVariant.name.capitalize()}Resources"
        if (!mProject.tasks.names.contains(taskName)) {
            return
        }
        TaskProvider verifyResTask = mProject.tasks.named(taskName)
        verifyResTask.get().enabled = false
    }

    private void processGenSafeArgsTask() {
        String taskName = "generateSafeArgs${mVariant.name.capitalize()}"
        if (!mProject.tasks.names.contains(taskName)) {
            return
        }
        TaskProvider genSafeArgsTask = mProject.tasks.named(taskName)
        genSafeArgsTask.configure {
            dependsOn(mExplodeTasks)
        }
    }

}
