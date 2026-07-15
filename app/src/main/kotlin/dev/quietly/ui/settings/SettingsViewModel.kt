package dev.quietly.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quietly.data.prefs.SecurePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsUiState(val retentionDays: Int = 90)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SecurePreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState(retentionDays = prefs.retentionDays))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setRetentionDays(days: Int) {
        prefs.retentionDays = days
        _uiState.update { it.copy(retentionDays = days) }
    }
}
