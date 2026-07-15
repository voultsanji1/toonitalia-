-keep class com.toonitalia.app.** { *; }
-keep class kotlinx.parcelize.** { *; }
-keep class org.jsoup.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.google.gson.** { *; }
-keep class androidx.media3.** { *; }
-keep class coil.** { *; }

# Kotlin Parcelize
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep names for reflection
-keepnames class * implements java.io.Serializable
-keepnames class * implements android.os.Parcelable

# Network security config
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}