package moe.antimony.hoshi.features.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the lookup popup needs in order to show or hide the grammar-analysis action. */
data class AiGrammarPopupSettings(
    val isEnabled: Boolean = false,
    val isConfigured: Boolean = false,
)

data class AiGrammarUiState(
    val settings: AiGrammarSettings = AiGrammarSettings(),
    val isLoadingSettings: Boolean = true,
    val isAnalyzing: Boolean = false,
) {
    val popupSettings: AiGrammarPopupSettings
        get() = AiGrammarPopupSettings(
            isEnabled = settings.enabled,
            isConfigured = settings.isConfigured,
        )
}

@HiltViewModel
internal class AiGrammarViewModel @Inject constructor(
    private val repository: AiGrammarRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiGrammarUiState())
    val uiState: StateFlow<AiGrammarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collectLatest { settings ->
                _uiState.update { state ->
                    state.copy(settings = settings, isLoadingSettings = false)
                }
            }
        }
    }

    /**
     * Runs one analysis for the lookup popup. The popup bridge is callback-based, so the result is
     * delivered through [onResult] rather than through [uiState].
     */
    fun analyzeAsync(
        sentence: String,
        word: String?,
        bookTitle: String?,
        forceRefresh: Boolean,
        onResult: (AiGrammarOutcome) -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true) }
            val outcome = runCatching {
                repository.analyze(
                    AiGrammarRequest(
                        sentence = sentence,
                        word = word,
                        bookTitle = bookTitle,
                        forceRefresh = forceRefresh,
                    ),
                )
            }.getOrElse { AiGrammarOutcome.Failure(AiGrammarFailure.Network) }
            _uiState.update { it.copy(isAnalyzing = false) }
            onResult(outcome)
        }
    }

    fun updateEnabled(value: Boolean) = updateSettings { it.copy(enabled = value) }

    fun updateApiKey(value: String) = updateSettings { it.copy(apiKey = value.trim()) }

    fun updateBaseUrl(value: String) = updateSettings {
        it.copy(baseUrl = value.trim().ifEmpty { DEFAULT_AI_GRAMMAR_BASE_URL })
    }

    fun updateModel(value: String) = updateSettings {
        it.copy(model = value.trim().ifEmpty { DEFAULT_AI_GRAMMAR_MODEL })
    }

    fun updateSystemPrompt(value: String) = updateSettings {
        it.copy(systemPrompt = value.ifBlank { DEFAULT_AI_GRAMMAR_SYSTEM_PROMPT })
    }

    fun resetSystemPrompt() = updateSettings { it.copy(systemPrompt = DEFAULT_AI_GRAMMAR_SYSTEM_PROMPT) }

    fun updateMaxTokens(value: Int) = updateSettings {
        it.copy(maxTokens = value.coerceIn(128, 8192))
    }

    fun updateTimeoutSeconds(value: Int) = updateSettings {
        it.copy(timeoutSeconds = value.coerceIn(5, 300))
    }

    fun updateThinkingEnabled(value: Boolean) = updateSettings { it.copy(thinkingEnabled = value) }

    private fun updateSettings(transform: (AiGrammarSettings) -> AiGrammarSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }
}
