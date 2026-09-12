package moe.antimony.hoshi.features.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Settings for the DeepSeek-backed grammar analysis that the lookup popup can run on the
 * sentence a looked-up word belongs to.
 *
 * The feature is bring-your-own-key: nothing is sent anywhere until [apiKey] is set.
 */
data class AiGrammarSettings(
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_AI_GRAMMAR_BASE_URL,
    val model: String = DEFAULT_AI_GRAMMAR_MODEL,
    val systemPrompt: String = DEFAULT_AI_GRAMMAR_SYSTEM_PROMPT,
    val maxTokens: Int = DEFAULT_AI_GRAMMAR_MAX_TOKENS,
    val timeoutSeconds: Int = DEFAULT_AI_GRAMMAR_TIMEOUT_SECONDS,
    val thinkingEnabled: Boolean = false,
) {
    /** Grammar analysis is offered once a key exists. There is no separate on/off switch. */
    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    val chatCompletionsUrl: String
        get() = baseUrl.trim().trimEnd('/') + "/chat/completions"

    val timeoutMillis: Int
        get() = timeoutSeconds.coerceIn(5, 300) * 1000
}

const val DEFAULT_AI_GRAMMAR_BASE_URL = "https://api.deepseek.com"
const val DEFAULT_AI_GRAMMAR_MODEL = "deepseek-flash"
const val DEFAULT_AI_GRAMMAR_MAX_TOKENS = 1024
const val DEFAULT_AI_GRAMMAR_TIMEOUT_SECONDS = 60

const val DEFAULT_AI_GRAMMAR_SYSTEM_PROMPT = """你是一位日语语法讲师。用户会给你一个日语句子，以及该句子中被查词的单词。请用简体中文解析这个句子。

要求：
1. 先给出整句的直译，再给出自然的中文意译。
2. 拆解句子成分：指出主语、谓语、宾语和修饰关系，并逐个说明助词的作用。
3. 列出句中谓语与修饰语的活用形：原形、活用类型、时态、语态、敬体或简体。
4. 指出句中出现的惯用型或句型，说明含义与接续方式。
5. 如有省略成分、口语化或书面语特征，补充其完整形式。
6. 最后给出 1 到 3 个可迁移的记忆点。

输出要求：
- 只输出纯文本，不要使用 Markdown 表格、代码块或 HTML 标签。
- 用简短分行和「・」等符号组织内容，总长度控制在 400 字以内。
- 不要复述题目，不要输出寒暄或与解析无关的内容。"""

interface AiGrammarSettingsRepository {
    val settings: Flow<AiGrammarSettings>
    suspend fun update(transform: (AiGrammarSettings) -> AiGrammarSettings)
}

class DataStoreAiGrammarSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : AiGrammarSettingsRepository {
    override val settings: Flow<AiGrammarSettings> = dataStore.data.map { preferences ->
        preferences.toAiGrammarSettings()
    }

    override suspend fun update(transform: (AiGrammarSettings) -> AiGrammarSettings) {
        dataStore.edit { preferences ->
            val next = transform(preferences.toAiGrammarSettings())
            preferences[KEY_API_KEY] = next.apiKey
            preferences[KEY_BASE_URL] = next.baseUrl
            preferences[KEY_MODEL] = next.model
            preferences[KEY_SYSTEM_PROMPT] = next.systemPrompt
            preferences[KEY_MAX_TOKENS] = next.maxTokens
            preferences[KEY_TIMEOUT_SECONDS] = next.timeoutSeconds
            preferences[KEY_THINKING_ENABLED] = next.thinkingEnabled
        }
    }

    private fun Preferences.toAiGrammarSettings(): AiGrammarSettings = AiGrammarSettings(
        apiKey = this[KEY_API_KEY].orEmpty(),
        baseUrl = this[KEY_BASE_URL] ?: DEFAULT_AI_GRAMMAR_BASE_URL,
        model = this[KEY_MODEL] ?: DEFAULT_AI_GRAMMAR_MODEL,
        systemPrompt = this[KEY_SYSTEM_PROMPT] ?: DEFAULT_AI_GRAMMAR_SYSTEM_PROMPT,
        maxTokens = this[KEY_MAX_TOKENS] ?: DEFAULT_AI_GRAMMAR_MAX_TOKENS,
        timeoutSeconds = this[KEY_TIMEOUT_SECONDS] ?: DEFAULT_AI_GRAMMAR_TIMEOUT_SECONDS,
        thinkingEnabled = this[KEY_THINKING_ENABLED] ?: false,
    )

    private companion object {
        val KEY_API_KEY = stringPreferencesKey("aiGrammarApiKey")
        val KEY_BASE_URL = stringPreferencesKey("aiGrammarBaseUrl")
        val KEY_MODEL = stringPreferencesKey("aiGrammarModel")
        val KEY_SYSTEM_PROMPT = stringPreferencesKey("aiGrammarSystemPrompt")
        val KEY_MAX_TOKENS = intPreferencesKey("aiGrammarMaxTokens")
        val KEY_TIMEOUT_SECONDS = intPreferencesKey("aiGrammarTimeoutSeconds")
        val KEY_THINKING_ENABLED = booleanPreferencesKey("aiGrammarThinkingEnabled")
    }
}

private val Context.aiGrammarSettingsDataStore by preferencesDataStore(name = "ai-grammar-settings")

fun Context.aiGrammarSettingsRepository(): AiGrammarSettingsRepository =
    DataStoreAiGrammarSettingsRepository(aiGrammarSettingsDataStore)
