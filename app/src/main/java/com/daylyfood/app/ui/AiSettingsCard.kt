package com.daylyfood.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daylyfood.app.data.ai.AiProviders

/**
 * AI configuration. Everything here is optional: with no key the app keeps working,
 * AI features simply stay off.
 */
@Composable
fun AiSettingsCard(vm: AiSettingsViewModel = hiltViewModel()) {
    val config by vm.config.collectAsStateWithLifecycle()
    val test by vm.test.collectAsStateWithLifecycle()
    var keyInput by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    val masked = remember(config.providerId, config.hasKey) { vm.maskedKey() }

    SectionCard {
        Text("AI 识图与建议", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "需要你自己的 API Key，费用由供应商收取。不填也能正常记帐，只是没有 AI 功能。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(10.dp))
        Text("供应商", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AiProviders.ALL.forEach { provider ->
                FilterChip(
                    selected = config.providerId == provider.id,
                    onClick = { vm.selectProvider(provider.id) },
                    label = { Text(provider.label) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            config.provider.hint + (config.provider.freeNote?.let { "（$it）" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(10.dp))
        Text("API Key", style = MaterialTheme.typography.titleSmall)
        if (config.hasKey && keyInput.isBlank()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "已保存：${masked ?: "••••"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.clearKey() }) { Text("清除") }
            }
        }
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text(if (config.hasKey) "输入新 Key 以替换" else "粘贴 API Key") },
            singleLine = true,
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "隐藏" else "显示") }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    vm.saveKey(keyInput)
                    keyInput = ""
                },
                enabled = keyInput.isNotBlank(),
            ) { Text("保存 Key") }
            OutlinedButton(
                onClick = vm::testConnection,
                enabled = config.hasKey && test != AiSettingsViewModel.TestState.Testing,
            ) { Text(if (test == AiSettingsViewModel.TestState.Testing) "测试中…" else "测试连接") }
        }
        when (val result = test) {
            AiSettingsViewModel.TestState.Idle -> Unit
            AiSettingsViewModel.TestState.Testing -> Unit
            is AiSettingsViewModel.TestState.Ok -> Text(
                "连通正常，模型回复：${result.reply}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            is AiSettingsViewModel.TestState.Failed -> Text(
                "连接失败：${result.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 10.dp))

        ToggleRow(
            title = "识图记餐",
            subtitle = if (config.provider.supportsVision) "拍一张，识别出菜并填入数值"
            else "${config.provider.label} 不支持识图",
            checked = config.visionEnabled,
            enabled = config.provider.supportsVision,
            onChange = vm::setVision,
        )
        ToggleRow(
            title = "每日总结",
            subtitle = "每天按设置时间总结前一天，会消耗少量额度",
            checked = config.summaryEnabled,
            enabled = true,
            onChange = vm::setSummary,
        )
        ToggleRow(
            title = "每周报告",
            subtitle = "每周一次趋势分析，默认关闭",
            checked = config.weeklyEnabled,
            enabled = true,
            onChange = vm::setWeekly,
        )

        HorizontalDivider(Modifier.padding(vertical = 10.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("每日调用上限", style = MaterialTheme.typography.titleSmall)
                Text(
                    "今天已用 ${config.usedToday} / ${config.dailyCallLimit} 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { vm.setDailyLimit(config.dailyCallLimit - 10) }) { Text("−10") }
            TextButton(onClick = { vm.setDailyLimit(config.dailyCallLimit + 10) }) { Text("+10") }
        }
        Spacer(Modifier.width(2.dp))
        Text(
            "上限用于防止 Key 被反复调用烧钱；达到上限后 AI 功能当天自动停用，记帐不受影响。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
