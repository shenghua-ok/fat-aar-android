# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keepparameternames
-dontoptimize
-dontshrink
-dontobfuscate
-dontpreverify
#for assembleRelease on Gradle 8.0+
-dontwarn com.bumptech.glide.disklrucache.DiskLruCache$Editor
-dontwarn com.bumptech.glide.disklrucache.DiskLruCache$Value
-dontwarn com.bumptech.glide.disklrucache.DiskLruCache
-dontwarn com.bumptech.glide.gifdecoder.GifDecoder$BitmapProvider
-dontwarn com.bumptech.glide.gifdecoder.GifDecoder
-dontwarn com.bumptech.glide.gifdecoder.GifHeader
-dontwarn com.bumptech.glide.gifdecoder.GifHeaderParser
-dontwarn com.bumptech.glide.gifdecoder.StandardGifDecoder
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.ForOverride
-dontwarn com.google.errorprone.annotations.concurrent.LazyInit
-dontwarn com.google.j2objc.annotations.RetainedWith
-dontwarn com.google.j2objc.annotations.Weak
-dontwarn com.kezong.demo.libaar.BR
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn java.lang.invoke.StringConcatFactory