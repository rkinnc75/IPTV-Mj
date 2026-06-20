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

# Keep line numbers for readable crash stack traces, hide the source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─── Gson / JSON models (reflection-based) ────────────────────────────────
# R8 must not rename/strip @SerializedName fields or the DTO classes Gson
# instantiates by reflection, or release JSON parsing returns null/empty.
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.iptvapp.data.api.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# ─── Room entities ────────────────────────────────────────────────────────
-keep @androidx.room.Entity class * { *; }
-keep class com.iptvapp.data.local.entities.** { *; }

# ─── Retrofit / OkHttp ────────────────────────────────────────────────────
-keep,allowobfuscation interface com.iptvapp.data.api.XtreamApiService
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
# Retrofit/Kotlin: keep suspend function Continuation generic signatures.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ─── Kotlin metadata (needed by Gson + reflection on data classes) ────────
-keep class kotlin.Metadata { *; }

# ─── Media3 / ExoPlayer ───────────────────────────────────────────────────
-dontwarn androidx.media3.**

# ─── Hilt generates and keeps its own components ──────────────────────────
-dontwarn dagger.hilt.**