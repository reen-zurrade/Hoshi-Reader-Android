package moe.antimony.hoshi.features.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiGrammarRepositoryTest {
    @Test
    fun reportsNotConfiguredWithoutApiKeyWithoutCallingTheEndpoint() = runTest {
        val client = FakeAiGrammarClient(AiGrammarOutcome.Success("不该被调用"))
        val repository = repository(AiGrammarSettings(apiKey = ""), client)

        assertEquals(
            AiGrammarOutcome.Failure(AiGrammarFailure.NotConfigured),
            repository.analyze(AiGrammarRequest(sentence = "猫が好きだ")),
        )
        assertEquals(0, client.prompts.size)
    }

    @Test
    fun rejectsBlankSentences() = runTest {
        val client = FakeAiGrammarClient(AiGrammarOutcome.Success("不该被调用"))
        val repository = repository(AiGrammarSettings(apiKey = "hoshi-secret"), client)

        assertEquals(
            AiGrammarOutcome.Failure(AiGrammarFailure.NoSentence),
            repository.analyze(AiGrammarRequest(sentence = "   ")),
        )
        assertEquals(0, client.prompts.size)
    }

    @Test
    fun promptCarriesSentenceWordAndBookTitle() = runTest {
        val client = FakeAiGrammarClient(AiGrammarOutcome.Success("解析"))
        val repository = repository(AiGrammarSettings(apiKey = "hoshi-secret"), client)

        repository.analyze(
            AiGrammarRequest(
                sentence = " 猫が好きだ ",
                word = "猫",
                bookTitle = "吾輩は猫である",
            ),
        )

        val prompt = client.prompts.single()
        assertTrue(prompt.contains("句子：猫が好きだ"))
        assertTrue(prompt.contains("被查词：猫"))
        assertTrue(prompt.contains("出处：吾輩は猫である"))
    }

    @Test
    fun omitsWordAndBookTitleLinesWhenTheyAreMissing() = runTest {
        val client = FakeAiGrammarClient(AiGrammarOutcome.Success("解析"))
        val repository = repository(AiGrammarSettings(apiKey = "hoshi-secret"), client)

        repository.analyze(AiGrammarRequest(sentence = "猫が好きだ"))

        val prompt = client.prompts.single()
        assertTrue(!prompt.contains("被查词："))
        assertTrue(!prompt.contains("出处："))
    }

    @Test
    fun cachesSuccessfulAnswersAndHonoursForcedRefresh() = runTest {
        val client = FakeAiGrammarClient(AiGrammarOutcome.Success("解析"))
        val repository = repository(AiGrammarSettings(apiKey = "hoshi-secret"), client)
        val request = AiGrammarRequest(sentence = "猫が好きだ", word = "猫")

        assertEquals(AiGrammarOutcome.Success("解析"), repository.analyze(request))
        assertEquals(AiGrammarOutcome.Success("解析"), repository.analyze(request))
        assertEquals(1, client.prompts.size)

        assertEquals(AiGrammarOutcome.Success("解析"), repository.analyze(request.copy(forceRefresh = true)))
        assertEquals(2, client.prompts.size)
    }

    @Test
    fun separatesCacheEntriesBySentence() = runTest {
        val client = FakeAiGrammarClient(AiGrammarOutcome.Success("解析"))
        val repository = repository(AiGrammarSettings(apiKey = "hoshi-secret"), client)

        repository.analyze(AiGrammarRequest(sentence = "猫が好きだ"))
        repository.analyze(AiGrammarRequest(sentence = "犬が好きだ"))

        assertEquals(2, client.prompts.size)
    }

    @Test
    fun doesNotCacheFailures() = runTest {
        val client = FakeAiGrammarClient(AiGrammarOutcome.Failure(AiGrammarFailure.Network))
        val repository = repository(AiGrammarSettings(apiKey = "hoshi-secret"), client)
        val request = AiGrammarRequest(sentence = "猫が好きだ")

        repository.analyze(request)
        repository.analyze(request)

        assertEquals(2, client.prompts.size)
    }

    private fun repository(
        settings: AiGrammarSettings,
        client: AiGrammarClient,
    ): AiGrammarRepository = AiGrammarRepository(
        settingsRepository = FakeAiGrammarSettingsRepository(MutableStateFlow(settings)),
        client = client,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private class FakeAiGrammarSettingsRepository(
        private val state: MutableStateFlow<AiGrammarSettings>,
    ) : AiGrammarSettingsRepository {
        override val settings: Flow<AiGrammarSettings> = state

        override suspend fun update(transform: (AiGrammarSettings) -> AiGrammarSettings) {
            state.value = transform(state.value)
        }
    }

    private class FakeAiGrammarClient(
        private val outcome: AiGrammarOutcome,
    ) : AiGrammarClient {
        val prompts = mutableListOf<String>()

        override fun analyze(settings: AiGrammarSettings, messages: List<AiGrammarMessage>): AiGrammarOutcome {
            // Single-turn requests carry exactly one user message, which is what these tests assert on.
            prompts += messages.lastOrNull { it.role == AiGrammarMessageRole.User }?.content.orEmpty()
            return outcome
        }
    }
}
