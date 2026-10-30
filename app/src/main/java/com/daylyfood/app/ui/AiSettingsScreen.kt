package com.daylyfood.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daylyfood.app.data.ai.EndpointNormalizer

/**
 * AI settings: the user supplies endpoint, model name and key themselves, then flips the
 * switches they actually want. Nothing here is required for logging food.
 */
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    vm: AiSettingsViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val config by vm.config.collectAsStateWithLifecycle()
    val test by vm.test.collectAsStateWithLifecycle()

    var url by remember(config.endpointUrl) { mutableStateOf(config.endpointUrl) }
    var model by remember(config.modelName) { mutableStateOf(config.modelName) }
    var key by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var priceIn by remember(config.pricePerMillionIn) {
        mutableStateOf(config.pricePerMillionIn.takeIf { it > 0 }?.toString() ?: "")
    }
    var priceOut by remember(config.pricePerMillionOut) {
        mutableStateOf(config.pricePerMillionOut.takeIf { it > 0 }?.toString() ?: "")
    }
    val masked = remember(config.hasKey) { vm.maskedKey() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "返回")
            }
            Text("AI 设置", style = MaterialTheme.typography.headlineSmall)
        }

        SectionCard {
            Text(
                "填你自己的模型接口（OpenAI 兼容格式）。费用由你和模型服务商结算，本应用不代收。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("接口地址") },
                placeholder = { Text("只填域名也行，会自动补 /chat/completions") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (config.endpointUrl.isNotBlank()) {
                Text(
                    "实际请求：${EndpointNormalizer.display(config.endpointUrl)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("模型名") },
                placeholder = { Text("照服务商文档抄，识图要选支持图片的") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "DeepSeek：地址 https://api.deepseek.com，模型 deepseek-flash（识图同款）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (config.hasKey && key.isBlank()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "已保存 Key：${masked ?: "••••"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = vm::clearKey) { Text("清除") }
                }
            }
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(if (config.hasKey) "输入新 Key 以替换" else "API Key") },
                singleLine = true,
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "隐藏" else "显示") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveConfig(url, model, key); key = "" }) { Text("保存") }
                OutlinedButton(
                    onClick = { vm.saveAndTest(url, model, key); key = "" },
                    enabled = test != AiSettingsViewModel.TestState.Testing,
                ) { Text(if (test == AiSettingsViewModel.TestState.Testing) "测试中…" else "测试连接") }
            }
            when (val result = test) {
                AiSettingsViewModel.TestState.Idle, AiSettingsViewModel.TestState.Testing -> Unit
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
            if (!config.isConfigured) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "三项都填完才能用；只认文字（营养成分表）不需要 AI，不填也能用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard {
            Text("功能开关", style = MaterialTheme.typography.titleSmall)
            ToggleRow(
                title = "识图记餐",
                subtitle = "拍照认菜并估算营养；关掉后拍照只认营养成分表",
                checked = config.visionEnabled,
                enabled = true,
                onChange = vm::setVision,
            )
            ToggleRow(
                title = "每日总结",
                subtitle = "每天总结前一天，会消耗少量额度",
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
        }

        SectionCard {
            Text("花费记录", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "只看你自己花了多少，不查余额。数字来自每次响应里的 usage 字段。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "累计 ${config.totalTokens} tokens" +
                    "（输入 ${config.promptTokens} / 输出 ${config.completionTokens}）",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                config.estimatedCost?.let { "估算花费 ¥%.4f".format(it) }
                    ?: "填下面单价后才能算钱",
                style = MaterialTheme.typography.bodySmall,
                color = if (config.estimatedCost != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PriceField(
                    label = "输入单价",
                    value = priceIn,
                    onChange = { priceIn = it },
                    modifier = Modifier.weight(1f),
                )
                PriceField(
                    label = "输出单价",
                    value = priceOut,
                    onChange = { priceOut = it },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "单位：元/百万 tokens，照服务商价目表填；填 0 就只记 token 不算钱。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                vm.setPrices(priceIn.toDoubleOrNull() ?: 0.0, priceOut.toDoubleOrNull() ?: 0.0)
            }) { Text("保存单价") }
        }

        SectionCard {
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
                "达到上限后 AI 当天停用，记帐不受影响。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            "API Key 用手机硬件密钥加密保存在本机，不会写进备份文件，换机需要重新填。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PriceField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text("元/百万") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
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
