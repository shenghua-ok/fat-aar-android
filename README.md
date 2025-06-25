# fat-aar-android

[![license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/shenghua-ok/fat-aar-android/blob/master/LICENSE)
[![](https://jitpack.io/v/shenghua-ok/fat-aar-android.svg)](https://jitpack.io/#shenghua-ok/fat-aar-android)

- [中文文档](./README_CN.md)

The solution of merging aar works with [AGP][8.2.2] and Gradle wrapper from 8.2.1 ~ 8.14.2

##### Alert: if you want to use fat-aar under AGP8.0(means 3.0~8.0-),please visit original author's repo:https://github.com/kezong/fat-aar-android

## Getting Started

### Step 1: Add classpath
#### Add snippet below to your root build script file:
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

### Step 2: Add plugin
Add snippet below to the `build.gradle` of your main android library:
```groovy
apply plugin: 'com.kezong.fat-aar'
```

### Step 3: Embed dependencies

Declare `embed` for the dependencies you want to merge in `build.gradle`. 

The usage is similar to `implementation`, like this:

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

### Transitive

#### Local Dependency
If you want to include local transitive dependencies in final artifact, you must add `embed` for transitive dependencies in your main library. 

For example, mainLib depend on subLib1, subLib1 depend on subLib2, If you want include all dependencies in the final artifact, you must add `embed` for subLib1 and subLib2 in mainLib `build.gradle`

#### Remote Dependency
If you want to inlcude all of the remote transitive dependencies which are in POM file, you need change the `transitive` value to true in your `build.gradle`, like this:
```groovy
fataar {
    /**
     * If transitive is true, local jar module and remote library's dependencies will be embed. (local aar module does not support)
     * If transitive is false, just embed first level dependency
     * Default value is false
     * @since 1.3.0
     */
    transitive = true
}
```
If you change the transitive value to true,and want to ignore a dependency in its POM file, you can add exclude keywords, like this:
```groovy
embed('com.facebook.fresco:fresco:1.11.0') {
    // exclude all dependencies
    transitive = false
    // exclude any group or module
    exclude(group:'com.facebook.soloader', module:'soloader')
}
```

**More usage see [example](./example).**

## About AAR File

AAR is a file format for android library.
The file itself is a zip file that containing useful stuff in android.
See [anatomy of an aar file here][2].

**support list for now:**

- [x] Flavors
- [x] AndroidManifest merge
- [x] Classes merge 
- [x] Jar merge
- [x] Res merge
- [x] Assets merge
- [x] Jni libs merge
- [x] R.txt merge
- [x] R.class merge
- [x] DataBinding merge
- [x] Proguard merge
- [x] Kotlin module merge
- [x] androidx.navigation


## Gradle Version Support
| Version | Gradle Plugin | Gradle |
| :--------: | :--------:|:-------:|
| 1.4.5 | 8.2.2 | 8.2.1 - 8.14.2 |

- [If under AGP_Gradle8.0 Visit here](<https://github.com/kezong/fat-aar-android>)


The following link which version of Gradle is required for each version of the Android Gradle plugin. For the best performance, you should use the latest possible version of both Gradle and the plugin.

[Plugin version and Required Gradle version](https://developer.android.google.cn/studio/releases/gradle-plugin.html)

## Version Log
- [1.4.5](<https://jitpack.io/#shenghua-ok/fat-aar-android/1.4.5>)
  - Compatible with AGP 8.2.2 and Gradle wrapper from 8.2.1 to 8.14.2
  - Compatible with androidx.navigation component.

## Known Defects or Issues
- **Application cannot directly rely on embedded project：** application cannot directly rely on your embedded project. It must rely on the AAR file compiled by your embedded project
  - For debugging convenience, you can use `embed` in the main library project when you choose to package aar. When you need to run the app directly, you can use `implementation` or `api`

- **Res merge conflicts.** If the library res folder and embedded dependencies res have the same res Id(mostly `string/app_name`). A duplicate resources build exception will be thrown. To avoid res conflicts:
  - consider using a prefix to each res Id, both in library res and aar dependencies if possible. 
  - Adding `android.disableResourceValidation=true` to `gradle.properties` can do a trick to skip the exception.
  
- **Proguard**
  - If `minifyEnabled` is set to true, classes not referenced in the project will be filtered according to Proguard rules during compile, resulting in ClassNotFound during app compile.
   Most AAR is SDK that provide interfaces. It is recommended that you carefully comb Proguard files and add keep rules.

## Thanks

* [kezong-fat-aar-android][1]
* [android-fat-aar][2]
* [fat-aar-plugin][5]

[1]: https://github.com/kezong/fat-aar-android
[2]: https://github.com/adwiv/android-fat-aar
[3]: https://developer.android.com/studio/projects/android-library.html#aar-contents
[4]: https://developer.android.com/studio/releases/gradle-plugin.html
[5]: https://github.com/Vigi0303/fat-aar-plugin
