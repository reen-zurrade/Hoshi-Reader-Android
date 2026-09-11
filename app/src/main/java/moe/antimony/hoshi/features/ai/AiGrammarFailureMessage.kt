package moe.antimony.hoshi.features.ai

import android.content.Context
import moe.antimony.hoshi.R

/** Maps a transport-level failure to the localized copy the grammar panel shows. */
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
