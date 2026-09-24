# Quietly ProGuard / R8 optimization rules

# Keep Hilt-generated components
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# Keep Room entities, DAOs, and database
-keep class dev.quietly.data.db.entity.** { *; }
-keep class dev.quietly.data.db.dao.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep WorkManager workers
-keep class dev.quietly.worker.** { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Suppress warnings for missing classes in debug-only libs
-dontwarn org.jetbrains.annotations.**
