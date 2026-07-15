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

    // ── onboarding ─────────────────────────────────────────────────────────────────────
    var onboardingComplete: Boolean
        get()      = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    // ── data retention ───────────────────────────────────────────────────────────
    /**
     * How many days of raw usage data to retain.
     * Default is now 90 days to support the full importance-engine window.
     * Users who previously stored 30 days will have their setting preserved;
     * new installs default to 90.
     */
    var retentionDays: Int
        get()      = prefs.getInt(KEY_RETENTION, 90)
        set(value) = prefs.edit().putInt(KEY_RETENTION, value).apply()

    // ── PIN lock (optional) ───────────────────────────────────────────────────────
    /** Null means PIN is not set. */
    var pinHash: String?
        get()      = prefs.getString(KEY_PIN, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_PIN).apply()
            else prefs.edit().putString(KEY_PIN, value).apply()
        }

    val pinEnabled: Boolean get() = pinHash != null

    // ── theme ───────────────────────────────────────────────────────────────────────
    var darkTheme: Boolean
        get()      = prefs.getBoolean(KEY_DARK_THEME, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()

    // ── optional online metadata lookup ────────────────────────────────────────────
    /**
     * When true, Quietly may fetch minimal app metadata (category, description)
     * from an external source to improve importance scoring.
     *
     * DEFAULT: false — core features never require network access.
     * The INTERNET permission is only requested when the user explicitly enables
     * this toggle from Settings, and is documented clearly in the UI.
     */
    var onlineMetadataEnabled: Boolean
        get()      = prefs.getBoolean(KEY_ONLINE_METADATA, false)
        set(value) = prefs.edit().putBoolean(KEY_ONLINE_METADATA, value).apply()

    // ── importance analysis window ──────────────────────────────────────────────
    /**
     * Number of days used as the primary analysis window for the importance engine.
     * Default is 90. Users can switch to 30 or 7 as secondary lenses.
     */
    var analysisWindowDays: Int
        get()      = prefs.getInt(KEY_ANALYSIS_WINDOW, 90)
        set(value) = prefs.edit().putInt(KEY_ANALYSIS_WINDOW, value).apply()

    companion object {
        private const val KEY_ONBOARDING       = "onboarding_complete"
        private const val KEY_RETENTION        = "retention_days"
        private const val KEY_PIN              = "app_pin_hash"
        private const val KEY_DARK_THEME       = "dark_theme"
        private const val KEY_ONLINE_METADATA  = "online_metadata_enabled"
        private const val KEY_ANALYSIS_WINDOW  = "analysis_window_days"
    }
}
