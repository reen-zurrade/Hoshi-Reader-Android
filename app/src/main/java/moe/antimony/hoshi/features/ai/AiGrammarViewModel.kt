package moe.antimony.hoshi.features.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
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

/**
 * One bubble in the grammar panel.
 *
 * A failed assistant turn carries the transport [AiGrammarFailure] instead of text: localization
 * belongs to the UI layer, which maps it with [aiGrammarFailureMessage].
 */
data class AiGrammarTurn(
    val role: AiGrammarMessageRole,
    val text: String,
    val failure: AiGrammarFailure? = null,
)

/** State of the grammar panel that the native text-selection toolbar opens. */
sealed interface AiGrammarPanelState {
    data object Hidden : AiGrammarPanelState

    data class Visible(
        val sentence: String,
        val turns: List<AiGrammarTurn>,
        val pending: Boolean,
    ) : AiGrammarPanelState
}

/** History turns (user + assistant) replayed to the model, on top of the always-kept first answer. */
internal const val MAX_AI_GRAMMAR_HISTORY_TURNS = 10

/**
 * Builds the message list for the next turn of a conversation.
 *
 * The first answer is always kept: it holds the analysis every follow-up builds on. Older turns are
 * dropped from the front, and the cut is moved forward to the next user turn so the list never starts
 * with a dangling assistant reply. Failed turns are skipped entirely.
 */
internal fun buildAiGrammarConversationMessages(
    firstPrompt: String,
    turns: List<AiGrammarTurn>,
    maxHistoryTurns: Int = MAX_AI_GRAMMAR_HISTORY_TURNS,
): List<AiGrammarMessage> {
    val usable = turns.filter { it.failure == null && it.text.isNotBlank() }
    val firstAnswer = usable.firstOrNull { it.role == AiGrammarMessageRole.Assistant }
    val history = usable.filter { it !== firstAnswer }
    val trimmed = if (history.size > maxHistoryTurns) {
        var start = history.size - maxHistoryTurns
        while (start < history.size && history[start].role != AiGrammarMessageRole.User) start++
        if (start >= history.size) history.takeLast(1) else history.subList(start, history.size)
    } else {
        history
    }
    return buildList {
        add(AiGrammarMessage(AiGrammarMessageRole.User, firstPrompt))
        firstAnswer?.let { add(AiGrammarMessage(AiGrammarMessageRole.Assistant, it.text)) }
        trimmed.forEach { add(AiGrammarMessage(it.role, it.text)) }
    }
}

@HiltViewModel
internal class AiGrammarViewModel @Inject constructor(
    private val repository: AiGrammarRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiGrammarUiState())
    val uiState: StateFlow<AiGrammarUiState> = _uiState.asStateFlow()

    private val _panelState = MutableStateFlow<AiGrammarPanelState>(AiGrammarPanelState.Hidden)
    val panelState: StateFlow<AiGrammarPanelState> = _panelState.asStateFlow()

    private var conversationJob: Job? = null
    private var firstPrompt: String? = null

    /**
     * Starts a new conversation for text the user selected with the platform's own selection handles.
     *
     * Unlike the lookup popup path this carries its own sentence, because the selection is not tied to
     * a lookup entry. Starting a conversation discards the previous one.
     */
    fun analyzeSelection(sentence: String, word: String? = null, bookTitle: String? = null) {
        val trimmed = sentence.trim()
        if (trimmed.isEmpty()) return
        val prompt = buildAiGrammarUserPrompt(trimmed, word.orEmpty(), bookTitle)
        firstPrompt = prompt
        conversationJob?.cancel()
        _panelState.value = AiGrammarPanelState.Visible(trimmed, emptyList(), pending = true)
        conversationJob = viewModelScope.launch {
            val outcome = runCatching {
                repository.analyze(AiGrammarRequest(sentence = trimmed, word = word, bookTitle = bookTitle))
            }.getOrElse { AiGrammarOutcome.Failure(AiGrammarFailure.Network) }
            appendAnswer(outcome)
        }
    }

    /** Sends a follow-up question in the current conversation. */
    fun ask(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return
        val current = _panelState.value as? AiGrammarPanelState.Visible ?: return
        if (current.pending) return
        val prompt = firstPrompt ?: return
        val withQuestion = current.copy(
            turns = current.turns + AiGrammarTurn(AiGrammarMessageRole.User, trimmed),
            pending = true,
        )
        _panelState.value = withQuestion
        conversationJob = viewModelScope.launch {
            val outcome = runCatching {
                repository.respond(buildAiGrammarConversationMessages(prompt, withQuestion.turns))
            }.getOrElse { AiGrammarOutcome.Failure(AiGrammarFailure.Network) }
            appendAnswer(outcome)
        }
    }

    /**
     * Re-runs the last question, dropping the failed bubble. Falls back to repeating the first
     * analysis when nothing has been asked yet.
     */
    fun retryLast() {
        val current = _panelState.value as? AiGrammarPanelState.Visible ?: return
        if (current.pending) return
        val prompt = firstPrompt ?: return
        val kept = current.turns.dropLastWhile { it.failure != null }
        if (kept.none { it.role == AiGrammarMessageRole.User }) {
            analyzeSelection(current.sentence)
            return
        }
        val pending = current.copy(turns = kept, pending = true)
        _panelState.value = pending
        conversationJob = viewModelScope.launch {
            val outcome = runCatching {
                repository.respond(buildAiGrammarConversationMessages(prompt, pending.turns))
            }.getOrElse { AiGrammarOutcome.Failure(AiGrammarFailure.Network) }
            appendAnswer(outcome)
        }
    }

    /** Closing the panel discards the conversation, matching the in-memory-only cache policy. */
    fun dismissPanel() {
        conversationJob?.cancel()
        conversationJob = null
        firstPrompt = null
        _panelState.value = AiGrammarPanelState.Hidden
    }

    private fun appendAnswer(outcome: AiGrammarOutcome) {
        val current = _panelState.value as? AiGrammarPanelState.Visible ?: return
        val turn = when (outcome) {
            is AiGrammarOutcome.Success -> AiGrammarTurn(AiGrammarMessageRole.Assistant, outcome.text)
            is AiGrammarOutcome.Failure -> AiGrammarTurn(AiGrammarMessageRole.Assistant, "", outcome.reason)
        }
        _panelState.value = current.copy(turns = current.turns + turn, pending = false)
    }

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
