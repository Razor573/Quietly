package dev.quietly.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferences @Inject constructor(
    @ApplicationContext ctx: Context
) {
    private val prefs: SharedPreferences = try {
        val master = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx, "quietly_secure_prefs", master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e("SecurePreferences", "Failed to init EncryptedSharedPreferences, resetting or falling back", e)
        try {
            ctx.deleteSharedPreferences("quietly_secure_prefs")
            val master = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx, "quietly_secure_prefs", master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e2: Exception) {
            ctx.getSharedPreferences("quietly_fallback_prefs", Context.MODE_PRIVATE)
        }
    }

    // ── onboarding ─────────────────────────────────────────────────────────────────────
    var onboardingComplete: Boolean
        get()      = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    // ── data retention ───────────────────────────────────────────────────────────
    var retentionDays: Int
        get()      = prefs.getInt(KEY_RETENTION, 90)
        set(value) = prefs.edit().putInt(KEY_RETENTION, value).apply()

    // ── PIN lock (optional) ───────────────────────────────────────────────────────
    private val _pinEnabledFlow = MutableStateFlow(prefs.getString(KEY_PIN, null) != null)
    val pinEnabledFlow: StateFlow<Boolean> = _pinEnabledFlow.asStateFlow()

    var pinHash: String?
        get()      = prefs.getString(KEY_PIN, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_PIN).apply()
            else prefs.edit().putString(KEY_PIN, value).apply()
            _pinEnabledFlow.value = value != null
        }

    val pinEnabled: Boolean get() = pinHash != null

    // ── theme ───────────────────────────────────────────────────────────────────────
    private val _darkThemeFlow = MutableStateFlow(prefs.getBoolean(KEY_DARK_THEME, true))
    val darkThemeFlow: StateFlow<Boolean> = _darkThemeFlow.asStateFlow()

    var darkTheme: Boolean
        get()      = prefs.getBoolean(KEY_DARK_THEME, true)
        set(value) {
            prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()
            _darkThemeFlow.value = value
        }

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
