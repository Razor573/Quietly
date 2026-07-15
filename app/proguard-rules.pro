# Quietly ProGuard rules

# Keep Hilt-generated components
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# Keep Room entities and DAOs
-keep class dev.quietly.data.db.entity.** { *; }
-keep class dev.quietly.data.db.dao.** { *; }

# Keep WorkManager workers
-keep class dev.quietly.worker.** { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Suppress warnings for missing classes in debug-only libs
-dontwarn org.jetbrains.annotations.**
