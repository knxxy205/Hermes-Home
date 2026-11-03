# Add project specific ProGuard rules here.
# JSch loads authentication, cipher, and key-exchange implementations by class name.
-keep class com.jcraft.jsch.** { *; }
# Preserve persisted WorkManager class names and their reflective constructors across updates.
-keep class com.example.data.worker.** { *; }

# Optional desktop agents, loggers and alternate crypto/TLS providers are not installed.
# JSch/OkHttp detect their availability before use; Android's platform providers remain used.
-dontwarn com.sun.jna.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
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
