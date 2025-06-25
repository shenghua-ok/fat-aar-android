# fat-aar-android
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/shenghua-ok/fat-aar-android/blob/master/LICENSE)
[![](https://jitpack.io/v/shenghua-ok/fat-aar-android.svg)](https://jitpack.io/#shenghua-ok/fat-aar-android)

该插件提供了将library以及它依赖的library一起打包成一个完整aar的解决方案，支持AGP 8.2.2 搭配 Gradle wrapper 8.2.1 ~ 8.14.2
##### 注意：你如果想要使用8.0以下的版本，请访问原作者仓库：https://github.com/kezong/fat-aar-android

## 如何使用

#### 第一步: Apply classpath
##### 添加以下代码到你工程根目录下的`build.gradle`文件中:
For Maven Central (The lastest release is available on [Jitpack Central](https://jitpack.io/#shenghua-ok/fat-aar-android/1.4.5)):
```groovy
buildscript {
    repositories {
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
    dependencies {
        classpath 'com.github.shenghua-ok:fat-aar-android:1.4.5'
    }
}
```

#### 第二步: Add plugin
添加以下代码到你的主library的`build.gradle`中:
```groovy
apply plugin: 'com.kezong.fat-aar'
```

#### 第三步: Embed dependencies
- `embed`你所需要的工程, 用法类似`implementation`

代码所示：
```groovy
dependencies {
    implementation fileTree(dir: 'libs', include: '*.jar')
    // java dependency
    embed project(path: ':lib-java', configuration: 'default')
    // aar dependency
    embed project(path: ':lib-aar', configuration: 'default')
    // aar dependency
    embed project(path: ':lib-aar2', configuration: 'default')
    // local full aar dependency, just build in flavor1
    flavor1Embed project(path: ':lib-aar-local', configuration: 'default')
    // local full aar dependency, just build in debug
    debugEmbed(name: 'lib-aar-local2', ext: 'aar')
    // remote jar dependency
    embed 'com.google.guava:guava:20.0'
    // remote aar dependency
    embed 'com.facebook.fresco:fresco:1.12.0'
    // don't want to embed in
    implementation('androidx.appcompat:appcompat:1.2.0')
}
```

### 第四步: 执行assemble命令

- 在你的工程目录下执行assemble指令，其中lib-main为你主library的工程名称，你可以根据不同的flavor以及不同的buildType来决定执行具体的assemble指令
```shell script
# assemble all 
./gradlew :lib-main:assemble

# assemble debug
./gradlew :lib-main:assembleDebug

# assemble flavor
./gradlew :lib-main:assembleFlavor1Debug
```
最终合并产物会覆盖原有aar，同时路径会打印在log信息中.

### 多级依赖

#### 本地依赖

如果你想将本地所有相关的依赖项全部包含在最终产物中，你需要在你主library中对所有依赖都加上`embed`关键字

比如，mainLib依赖lib1，lib1依赖lib2，如果你想将所有依赖都打入最终产物，你必须在mainLib的`build.gradle`中对lib1以及lib2都加上`embed`关键字

#### 远程依赖

如果你想将所有远程依赖在pom中声明的依赖项同时打入在最终产物里的话，你需要在`build.gradle`中将transitive值改为true，例如：
```groovy
fataar {
    /**
     * If transitive is true, local jar module and remote library's dependencies will be embed.
     * If transitive is false, just embed first level dependency
     * Local aar project does not support transitive, always embed first level
     * Default value is false
     * @since 1.3.0
     */
    transitive = true
}
```

如果你将transitive的值改成了true，并且想忽略pom文件中的某一个依赖项，你可以添加`exclude`关键字，例如：
```groovy
embed('com.facebook.fresco:fresco:1.11.0') {
    // exclude any group or module
    exclude(group:'com.facebook.soloader', module:'soloader')
    // exclude all dependencies
    transitive = false
}
```

**更多使用方式可参考 [example](./example).**

## 关于 AAR 文件
AAR是Android提供的一种官方文件形式；
该文件本身是一个Zip文件，并且包含Android里所有的元素；
可以参考 [aar文件详解][2].

**支持功能列表:**

- [x] 支持flavor配置
- [x] AndroidManifest合并
- [x] Classes合并
- [x] Jar合并
- [x] Res合并
- [x] Assets合并
- [x] Jni合并
- [x] R.txt合并
- [x] R.class合并
- [x] DataBinding合并
- [x] Proguard合并
- [x] Kotlin module合并
- [x] 支持 androidx.navigation

## Gradle版本支持

| Version | Gradle Plugin | Gradle |
| :--------: | :--------:|:-------:|
| 1.4.5 | 8.2.2 | 8.2.1 - 8.14.2 |

- [If under AGP_Gradle8.0 please Visit here](<https://github.com/kezong/fat-aar-android>)

[Gradle Plugin和所需求的Gradle版本官方文档](https://developer.android.google.cn/studio/releases/gradle-plugin.html)

## 更新日志
- [1.4.5](<https://jitpack.io/#shenghua-ok/fat-aar-android/1.4.5>)
  - Compatible with AGP 8.2.2 and Gradle wrapper from 8.2.1 to 8.14.2
  - Compatible with androidx.navigation component.
  
## 常见问题

- **Application无法直接依赖embed工程：** application无法直接依赖你的embed工程，必须依赖你embed工程所编译生成的aar文件
  - 为了调试方便，你可以在选择在打包aar时，在主library工程中使用`embed`，需要直接运行app时，采用`implementation`或者`api`

- **资源冲突：** 如果library和module中含有同名的资源(比如 `string/app_name`)，编译将会报`duplication resources`的相关错误，有两种方法可以解决这个问题：
  - 考虑将library以及module中的资源都加一个前缀来避免资源冲突； 
  - 在`gradle.properties`中添加`android.disableResourceValidation=true`可以忽略资源冲突的编译错误，程序会采用第一个找到的同名资源作为实际资源.

- **关于混淆**
  - 如果`minifyEnabled`设置为true，编译时会根据proguard规则过滤工程中没有引用到的类，导致App集成时找不到对象，因为大多数AAR都是提供接口的SDK，建议大家仔细梳理proguard文件。

## 致谢

* [android-fat-aar-3.0~7.0][1]
* [android-fat-aar][2]
* [fat-aar-plugin][5]

[1]: https://github.com/kezong/fat-aar-android
[2]: https://github.com/adwiv/android-fat-aar
[3]: https://developer.android.com/studio/projects/android-library.html#aar-contents
[4]: https://developer.android.com/studio/releases/gradle-plugin.html
[5]: https://github.com/Vigi0303/fat-aar-plugin
