package moe.antimony.hoshi.features.ai

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.antimony.hoshi.R

/**
 * Reply payload the lookup popup receives for the `aiGrammar` request. It is encoded as JSON and
 * interpolated into `window.hoshiReaderPopupHost.resolveMessage(...)`, so it must stay valid JSON.
 */
@Serializable
internal data class AiGrammarPopupReply(
    val ok: Boolean,
    val text: String? = null,
    val message: String? = null,
)

internal fun AiGrammarPopupReply.toJson(): String = popupJson.encodeToString(this)

/** Maps a transport-level failure to the localized copy the popup shows. */
internal fun aiGrammarFailureMessage(context: Context, reason: AiGrammarFailure): String =
    context.getString(
        when (reason) {
            AiGrammarFailure.NotConfigured -> R.string.ai_grammar_error_not_configured
            AiGrammarFailure.NoSentence -> R.string.ai_grammar_error_no_sentence
            AiGrammarFailure.Network -> R.string.ai_grammar_error_network
            AiGrammarFailure.Timeout -> R.string.ai_grammar_error_timeout
            AiGrammarFailure.Auth -> R.string.ai_grammar_error_auth
            AiGrammarFailure.RateLimited -> R.string.ai_grammar_error_rate_limited
            AiGrammarFailure.Quota -> R.string.ai_grammar_error_quota
            AiGrammarFailure.InvalidRequest -> R.string.ai_grammar_error_invalid_request
            AiGrammarFailure.Server -> R.string.ai_grammar_error_server
            AiGrammarFailure.EmptyResponse -> R.string.ai_grammar_error_empty
        },
    )

private val popupJson = Json { encodeDefaults = false }
