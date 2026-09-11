package moe.antimony.hoshi.features.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiGrammarConversationTest {
    @Test
    fun sendsTheFirstPromptAndAnswerBeforeLaterTurns() {
        val messages = buildAiGrammarConversationMessages(
            firstPrompt = "解析：A",
            turns = listOf(
                assistant("解析结果"),
                AiGrammarTurn(AiGrammarMessageRole.User, "问题一"),
                assistant("回答一"),
            ),
        )

        assertEquals(
            listOf(
                AiGrammarMessageRole.User to "解析：A",
                AiGrammarMessageRole.Assistant to "解析结果",
                AiGrammarMessageRole.User to "问题一",
                AiGrammarMessageRole.Assistant to "回答一",
            ),
            messages.map { it.role to it.content },
        )
    }

    @Test
    fun skipsFailedTurnsSoTheHistoryNeverCarriesAnEmptyReply() {
        val messages = buildAiGrammarConversationMessages(
            firstPrompt = "解析：A",
            turns = listOf(
                assistant("解析结果"),
                AiGrammarTurn(AiGrammarMessageRole.User, "问题一"),
                AiGrammarTurn(AiGrammarMessageRole.Assistant, "", AiGrammarFailure.Network),
                AiGrammarTurn(AiGrammarMessageRole.User, "问题二"),
            ),
        )

        // 问题一 survives: its question is still part of the conversation, only the failed reply is gone.
        assertEquals(
            listOf(
                AiGrammarMessageRole.User to "解析：A",
                AiGrammarMessageRole.Assistant to "解析结果",
                AiGrammarMessageRole.User to "问题一",
                AiGrammarMessageRole.User to "问题二",
            ),
            messages.map { it.role to it.content },
        )
    }

    @Test
    fun keepsTheFirstAnswerAndDropsTheOldestTurnsWhenHistoryGrows() {
        val turns = buildList {
            add(assistant("解析结果"))
            repeat(8) { index ->
                add(AiGrammarTurn(AiGrammarMessageRole.User, "问$index"))
                add(assistant("答$index"))
            }
        }

        val messages = buildAiGrammarConversationMessages(
            firstPrompt = "解析：A",
            turns = turns,
            maxHistoryTurns = 4,
        )

        // first prompt + first answer + the 4 most recent history turns
        assertEquals(6, messages.size)
        assertEquals("解析：A", messages[0].content)
        assertEquals("解析结果", messages[1].content)
        assertEquals(AiGrammarMessageRole.User, messages[2].role)
        assertEquals("问6", messages[2].content)
        assertEquals("答7", messages.last().content)
    }

    @Test
    fun trimsForwardToAUserTurnSoTheListNeverStartsWithAnAnswer() {
        val turns = listOf(
            assistant("解析结果"),
            AiGrammarTurn(AiGrammarMessageRole.User, "问一"),
            assistant("答一"),
            AiGrammarTurn(AiGrammarMessageRole.User, "问二"),
        )

        val messages = buildAiGrammarConversationMessages(
            firstPrompt = "解析：A",
            turns = turns,
            maxHistoryTurns = 1,
        )

        assertEquals(
            listOf(
                AiGrammarMessageRole.User to "解析：A",
                AiGrammarMessageRole.Assistant to "解析结果",
                AiGrammarMessageRole.User to "问二",
            ),
            messages.map { it.role to it.content },
        )
    }

    private fun assistant(text: String) = AiGrammarTurn(AiGrammarMessageRole.Assistant, text)
}
