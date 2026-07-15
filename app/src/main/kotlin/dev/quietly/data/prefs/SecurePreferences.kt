package dev.quietly.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferences @Inject constructor(
    @ApplicationContext ctx: Context
) {
    private val master = MasterKey.Builder(ctx)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        ctx, "quietly_secure_prefs", master,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ── onboarding ──────────────────────────────────────────────────────────
    var onboardingComplete: Boolean
        get()      = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    // ── data retention ──────────────────────────────────────────────────────
    var retentionDays: Int
        get()      = prefs.getInt(KEY_RETENTION, 30)
        set(value) = prefs.edit().putInt(KEY_RETENTION, value).apply()

    // ── PIN lock (optional) ──────────────────────────────────────────────────
    /** Null means PIN is not set. Stored as bcrypt-like hash is overkill for
     *  a local PIN; we store the raw digits encrypted by AES-256-GCM above. */
    var pinHash: String?
        get()      = prefs.getString(KEY_PIN, null)
        set(value) { if (value == null) prefs.edit().remove(KEY_PIN).apply()
                     else prefs.edit().putString(KEY_PIN, value).apply() }

    val pinEnabled: Boolean get() = pinHash != null

    // ── theme ────────────────────────────────────────────────────────────────
    var darkTheme: Boolean
        get()      = prefs.getBoolean(KEY_DARK_THEME, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()

    companion object {
        private const val KEY_ONBOARDING = "onboarding_complete"
        private const val KEY_RETENTION  = "retention_days"
        private const val KEY_PIN        = "app_pin_hash"
        private const val KEY_DARK_THEME = "dark_theme"
    }
}
