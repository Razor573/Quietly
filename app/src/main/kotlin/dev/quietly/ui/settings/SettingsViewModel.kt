package dev.quietly.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quietly.data.prefs.SecurePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class SettingsUiState(
    val retentionDays: Int     = 30,
    val darkTheme:     Boolean = true,
    val pinEnabled:    Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SecurePreferences
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            retentionDays = prefs.retentionDays,
            darkTheme     = prefs.darkTheme,
            pinEnabled    = prefs.pinEnabled
        )
    )
    val uiState: StateFlow<SettingsUiState> = _state

    fun setRetention(days: Int) {
        prefs.retentionDays = days
        _state.value = _state.value.copy(retentionDays = days)
    }

    fun setDarkTheme(dark: Boolean) {
        prefs.darkTheme = dark
        _state.value = _state.value.copy(darkTheme = dark)
    }

    fun setPin(pin: String?) {
        prefs.pinHash = pin
        _state.value = _state.value.copy(pinEnabled = pin != null)
    }
}
