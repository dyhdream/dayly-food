package com.daylyfood.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daylyfood.app.data.update.UpdateInfo

/**
 * Update prompt. Optional releases can be postponed ("下次再说" remembers the version);
 * important ones can only be installed or retried, never dismissed.
 */
@Composable
fun UpdateDialog(
    state: UpdateViewModel.State,
    onInstall: (UpdateInfo) -> Unit,
    onPostpone: (String) -> Unit,
    onRetry: (String?) -> Unit,
    onBrowser: () -> Unit,
) {
    when (state) {
        is UpdateViewModel.State.Available -> {
            val info = state.info
            AlertDialog(
                onDismissRequest = { if (!info.mandatory) onPostpone(info.version) },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("发现新版本 v${info.version}")
                        if (info.mandatory) {
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(
                                "重要更新",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(6.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                },
                text = {
                    Column(
                        Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (info.mandatory) {
                            Text(
                                "这一版包含必须更新的改动，请安装后继续使用。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(info.notes.take(2000), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "安装包 ${"%.1f".format(info.sizeBytes / 1024.0 / 1024.0)} MB · 覆盖安装，记录不会丢",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { onInstall(info) }) { Text("下载并安装") }
                },
                dismissButton = if (info.mandatory) {
                    { TextButton(onClick = onBrowser) { Text("浏览器下载") } }
                } else {
                    { TextButton(onClick = { onPostpone(info.version) }) { Text("下次再说") } }
                },
            )
        }

        is UpdateViewModel.State.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载 v${state.version}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${state.percent}%", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {},
        )

        is UpdateViewModel.State.Failed -> AlertDialog(
            onDismissRequest = { if (state.version == null) onRetry(null) },
            title = { Text("更新失败") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = { onRetry(state.version) }) { Text("重试") } },
            dismissButton = { TextButton(onClick = onBrowser) { Text("浏览器下载") } },
        )

        else -> Unit
    }
}
