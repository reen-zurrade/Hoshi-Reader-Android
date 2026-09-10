package moe.antimony.hoshi.features.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import moe.antimony.hoshi.R

/**
 * Localized copy the lookup popup iframe needs to render the grammar-analysis action. Built by the
 * Compose surfaces (which own localization) and handed to the pure HTML renderer.
 */
data class AiGrammarPopupLabels(
    val isEnabled: Boolean = false,
    val isConfigured: Boolean = false,
    val actionLabel: String = "",
    val pendingLabel: String = "",
    val errorLabel: String = "",
    val refreshLabel: String = "",
)

@Composable
internal fun aiGrammarPopupLabels(
    isEnabled: Boolean,
    isConfigured: Boolean,
): AiGrammarPopupLabels = AiGrammarPopupLabels(
    isEnabled = isEnabled,
    isConfigured = isConfigured,
    actionLabel = stringResource(R.string.ai_grammar_action),
    pendingLabel = stringResource(R.string.ai_grammar_pending),
    errorLabel = stringResource(R.string.ai_grammar_error_generic),
    refreshLabel = stringResource(R.string.ai_grammar_refresh),
)
