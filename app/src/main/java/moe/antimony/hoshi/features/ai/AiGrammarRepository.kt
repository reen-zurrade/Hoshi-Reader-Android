package moe.antimony.hoshi.features.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One grammar-analysis request for the sentence a looked-up word belongs to. */
data class AiGrammarRequest(
    val sentence: String,
    val word: String? = null,
    val bookTitle: String? = null,
    val forceRefresh: Boolean = false,
)

@Singleton
internal class AiGrammarRepository(
    private val settingsRepository: AiGrammarSettingsRepository,
    private val client: AiGrammarClient,
    private val ioDispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        settingsRepository: AiGrammarSettingsRepository,
    ) : this(
        settingsRepository = settingsRepository,
        client = DeepSeekAiGrammarClient(),
        ioDispatcher = Dispatchers.IO,
    )

    val settings: Flow<AiGrammarSettings> = settingsRepository.settings

    suspend fun updateSettings(transform: (AiGrammarSettings) -> AiGrammarSettings) {
        settingsRepository.update(transform)
    }

    /**
     * Answers with the analysis for [AiGrammarRequest.sentence]. Successful answers are memoized so
     * re-tapping the same word in the same sentence does not pay for a second request.
     */
    suspend fun analyze(request: AiGrammarRequest): AiGrammarOutcome {
        val settings = settingsRepository.settings.first()
        if (!settings.isConfigured) return AiGrammarOutcome.Failure(AiGrammarFailure.NotConfigured)

        val sentence = request.sentence.trim()
        if (sentence.isEmpty()) return AiGrammarOutcome.Failure(AiGrammarFailure.NoSentence)
        val word = request.word?.trim().orEmpty()

        val key = cacheKey(settings, sentence, word)
        if (!request.forceRefresh) {
            cached(key)?.let { return AiGrammarOutcome.Success(it) }
        }

        val outcome = withContext(ioDispatcher) {
            client.analyze(settings, buildAiGrammarUserPrompt(sentence, word, request.bookTitle))
        }
        if (outcome is AiGrammarOutcome.Success) {
            store(key, outcome.text)
        }
        return outcome
    }

    private suspend fun cached(key: String): String? = cacheLock.withLock { cache[key] }

    private suspend fun store(key: String, value: String) {
        cacheLock.withLock {
            cache[key] = value
            while (cache.size > MAX_CACHE_ENTRIES) {
                val eldest = cache.entries.firstOrNull() ?: break
                cache.remove(eldest.key)
            }
        }
    }

    private fun cacheKey(settings: AiGrammarSettings, sentence: String, word: String): String =
        buildString {
            append(settings.model)
            append('\u0000')
            append(settings.systemPrompt.hashCode())
            append('\u0000')
            append(word)
            append('\u0000')
            append(sentence)
        }

    private val cacheLock = Mutex()
    private val cache = LinkedHashMap<String, String>(16, 0.75f, true)

    private companion object {
        const val MAX_CACHE_ENTRIES = 64
    }
}

/** Builds the user turn sent to the model; kept separate so its shape is testable. */
internal fun buildAiGrammarUserPrompt(sentence: String, word: String, bookTitle: String?): String =
    buildString {
        append("请解析下面这个日语句子。\n\n")
        append("句子：")
        append(sentence.trim())
        append('\n')
        if (word.isNotBlank()) {
            append("被查词：")
            append(word)
            append('\n')
        }
        val title = bookTitle?.trim().orEmpty()
        if (title.isNotEmpty()) {
            append("出处：")
            append(title)
            append('\n')
        }
        append("\n请按系统提示的要求给出解析。")
    }
