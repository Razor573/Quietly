package dev.quietly.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM encrypted SharedPreferences backed by Android Keystore.
 * Used for app settings that must not be readable from an unencrypted backup.
 */
@Singleton
class SecurePreferences @Inject constructor(@ApplicationContext context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "quietly_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var onboardingComplete: Boolean
        get()  = prefs.getBoolean(KEY_ONBOARDING, false)
        set(v) = prefs.edit().putBoolean(KEY_ONBOARDING, v).apply()

    var retentionDays: Int
        get()  = prefs.getInt(KEY_RETENTION, 90)
        set(v) = prefs.edit().putInt(KEY_RETENTION, v).apply()

    companion object {
        private const val KEY_ONBOARDING = "onboarding_complete"
        private const val KEY_RETENTION  = "retention_days"
    }
}
