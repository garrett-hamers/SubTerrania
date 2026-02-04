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

# Jetpack Compose
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# Keep Compose runtime classes
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.animation.** { *; }

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

# AndroidX Lifecycle
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class * implements androidx.lifecycle.LifecycleObserver {
    <init>(...);
}

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
