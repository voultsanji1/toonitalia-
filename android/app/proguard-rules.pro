-keepattributes *Annotation*
-keep class com.toonitalia.app.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
