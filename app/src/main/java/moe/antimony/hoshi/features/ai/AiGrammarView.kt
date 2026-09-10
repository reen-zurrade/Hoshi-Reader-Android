package moe.antimony.hoshi.features.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.settings.SettingsDetailScaffold
import moe.antimony.hoshi.ui.hoshiOutlinedTextFieldColors
import moe.antimony.hoshi.ui.hoshiSingleLineTextFieldLineLimits
import moe.antimony.hoshi.ui.rememberSyncedTextFieldState

private enum class AiGrammarField {
    ApiKey,
    BaseUrl,
    Model,
    MaxTokens,
    Timeout,
    SystemPrompt,
}

@Composable
fun AiGrammarView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AiGrammarViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = uiState.settings
    var editing by remember { mutableStateOf<AiGrammarField?>(null) }
    val noneLabel = stringResource(R.string.none)

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_ai_grammar),
        onClose = onClose,
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                AiGrammarCard {
                    AiGrammarSwitchRow(
                        label = stringResource(R.string.ai_grammar_enable),
                        checked = settings.enabled,
                        onCheckedChange = viewModel::updateEnabled,
                    )
                }
            }

            item {
                Text(
                    text = if (settings.isConfigured) {
                        stringResource(R.string.ai_grammar_status_ready)
                    } else {
                        stringResource(R.string.ai_grammar_status_missing_key)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }

            item {
                AiGrammarCard {
                    AiGrammarValueRow(
                        label = stringResource(R.string.ai_grammar_api_key),
                        value = if (settings.apiKey.isBlank()) "" else "••••••••",
                        noneLabel = noneLabel,
                        onClick = { editing = AiGrammarField.ApiKey },
                    )
                    AiGrammarValueRow(
                        label = stringResource(R.string.ai_grammar_base_url),
                        value = settings.baseUrl,
                        noneLabel = noneLabel,
                        onClick = { editing = AiGrammarField.BaseUrl },
                    )
                    AiGrammarValueRow(
                        label = stringResource(R.string.ai_grammar_model),
                        value = settings.model,
                        noneLabel = noneLabel,
                        onClick = { editing = AiGrammarField.Model },
                    )
                    AiGrammarValueRow(
                        label = stringResource(R.string.ai_grammar_max_tokens),
                        value = settings.maxTokens.toString(),
                        noneLabel = noneLabel,
                        onClick = { editing = AiGrammarField.MaxTokens },
                    )
                    AiGrammarValueRow(
                        label = stringResource(R.string.ai_grammar_timeout),
                        value = settings.timeoutSeconds.toString(),
                        noneLabel = noneLabel,
                        onClick = { editing = AiGrammarField.Timeout },
                        showDivider = false,
                    )
                }
            }

            item {
                AiGrammarCard {
                    AiGrammarValueRow(
                        label = stringResource(R.string.ai_grammar_system_prompt),
                        value = settings.systemPrompt,
                        noneLabel = noneLabel,
                        onClick = { editing = AiGrammarField.SystemPrompt },
                    )
                    AiGrammarSwitchRow(
                        label = stringResource(R.string.ai_grammar_thinking),
                        checked = settings.thinkingEnabled,
                        onCheckedChange = viewModel::updateThinkingEnabled,
                    )
                }
            }

            item {
                TextButton(onClick = viewModel::resetSystemPrompt) {
                    Text(stringResource(R.string.ai_grammar_reset_prompt))
                }
            }

            item {
                Text(
                    text = stringResource(R.string.ai_grammar_privacy_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    when (editing) {
        null -> Unit
        AiGrammarField.ApiKey -> AiGrammarTextDialog(
            title = stringResource(R.string.ai_grammar_api_key),
            value = settings.apiKey,
            secure = true,
            onDismiss = { editing = null },
            onSave = {
                viewModel.updateApiKey(it)
                editing = null
            },
        )
        AiGrammarField.BaseUrl -> AiGrammarTextDialog(
            title = stringResource(R.string.ai_grammar_base_url),
            value = settings.baseUrl,
            onDismiss = { editing = null },
            onSave = {
                viewModel.updateBaseUrl(it)
                editing = null
            },
        )
        AiGrammarField.Model -> AiGrammarTextDialog(
            title = stringResource(R.string.ai_grammar_model),
            value = settings.model,
            onDismiss = { editing = null },
            onSave = {
                viewModel.updateModel(it)
                editing = null
            },
        )
        AiGrammarField.MaxTokens -> AiGrammarTextDialog(
            title = stringResource(R.string.ai_grammar_max_tokens),
            value = settings.maxTokens.toString(),
            onDismiss = { editing = null },
            onSave = { raw ->
                raw.trim().toIntOrNull()?.let(viewModel::updateMaxTokens)
                editing = null
            },
        )
        AiGrammarField.Timeout -> AiGrammarTextDialog(
            title = stringResource(R.string.ai_grammar_timeout),
            value = settings.timeoutSeconds.toString(),
            onDismiss = { editing = null },
            onSave = { raw ->
                raw.trim().toIntOrNull()?.let(viewModel::updateTimeoutSeconds)
                editing = null
            },
        )
        AiGrammarField.SystemPrompt -> AiGrammarTextDialog(
            title = stringResource(R.string.ai_grammar_system_prompt),
            value = settings.systemPrompt,
            multiline = true,
            onDismiss = { editing = null },
            onSave = {
                viewModel.updateSystemPrompt(it)
                editing = null
            },
        )
    }
}

@Composable
private fun AiGrammarCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Column(content = { content() })
    }
}

@Composable
private fun AiGrammarSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun AiGrammarValueRow(
    label: String,
    value: String,
    noneLabel: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value.ifBlank { noneLabel },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun AiGrammarTextDialog(
    title: String,
    value: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    secure: Boolean = false,
    multiline: Boolean = false,
) {
    var draft by remember(title, value) { mutableStateOf(value) }
    val scrollState = rememberScrollState()
    val fieldState = rememberSyncedTextFieldState(
        value = draft,
        onValueChange = { draft = it },
        scrollState = scrollState,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (secure) {
                OutlinedSecureTextField(
                    state = fieldState,
                    label = { Text(title) },
                    textObfuscationMode = TextObfuscationMode.Hidden,
                    colors = hoshiOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    state = fieldState,
                    label = { Text(title) },
                    lineLimits = if (multiline) {
                        // Positional arguments: (minHeightInLines, maxHeightInLines).
                        TextFieldLineLimits.MultiLine(6, 12)
                    } else {
                        hoshiSingleLineTextFieldLineLimits()
                    },
                    scrollState = scrollState,
                    colors = hoshiOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(draft) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
