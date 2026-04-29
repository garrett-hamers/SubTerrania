# ProGuard rules for SubTerrania

# Keep application class names for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# NOTE: Compose-specific keep rules are intentionally omitted. The Compose
# compiler plugin and the Compose libraries ship their own consumer ProGuard
# rules via consumerProguardFiles, which are sufficient. Keeping all of
# androidx.compose.** defeats R8 shrinking and bloats the release APK.

# Keep data classes used in game state (for potential serialization)
-keepclassmembers class com.atlyn.subterranea.domain.model.** {
    <fields>;
    <init>(...);
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Lifecycle / ViewModel — only what's needed for reflection-based instantiation.
-keepclassmembers class * implements androidx.lifecycle.LifecycleObserver {
    <init>(...);
}
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
