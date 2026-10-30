package com.daylyfood.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daylyfood.app.BuildConfig
import com.daylyfood.app.data.db.ProfileEntity
import com.daylyfood.app.data.ai.AiConfig
import kotlin.math.roundToInt

/** "我的" is a hub: every heavy setting lives on its own screen. */
@Composable
fun MeScreen(
    profile: ProfileEntity?,
    onOpenProfile: () -> Unit,
    onOpenAi: () -> Unit,
    updateVm: UpdateViewModel = hiltViewModel(),
    backupVm: BackupViewModel = hiltViewModel(),
    meVm: MeViewModel = hiltViewModel(),
) {
    val updateState by updateVm.state.collectAsStateWithLifecycle()
    val backupState by backupVm.state.collectAsStateWithLifecycle()
    val ai by meVm.aiConfig.collectAsStateWithLifecycle()
    val cacheBytes by meVm.cacheBytes.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            backupVm.export { json ->
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }.getOrNull() != null
            }
        }
    }

    var pendingRestore by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingRestore = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("我的", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            "记录只在本机，记得偶尔备份",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionCard {
            EntryLink(
                title = "个人信息",
                subtitle = profile?.let {
                    "${it.heightCm.roundToInt()} cm · ${it.weightKg} kg · ${goalLabel(it.goal)}"
                } ?: "还没填，先填一下",
                onClick = onOpenProfile,
            )
            EntryLink(
                title = "AI 设置",
                subtitle = aiSubtitle(ai),
                onClick = onOpenAi,
            )
        }

        SectionCard {
            Text("数据备份", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "导出成一个文件，卸载或换机后能恢复。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch(backupVm.suggestedFileName()) }) {
                    Text("导出备份")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                ) { Text("恢复备份") }
            }
            when (val backup = backupState) {
                BackupViewModel.State.Idle -> Unit
                BackupViewModel.State.Working ->
                    Text("处理中…", style = MaterialTheme.typography.bodySmall)

                is BackupViewModel.State.Done -> Text(
                    backup.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )

                is BackupViewModel.State.Failed -> Text(
                    backup.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("存储", style = MaterialTheme.typography.titleSmall)
                    Text(
                        cacheBytes?.let { "临时文件 ${formatBytes(it)}" } ?: "正在统计…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { meVm.clearCache() }) { Text("清理") }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "拍照的原图、下载过的更新包会自动清掉；这里只显示还没清的部分。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            Text("应用更新", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text("当前版本 v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            when (val update = updateState) {
                UpdateViewModel.State.Idle ->
                    TextButton(onClick = updateVm::check) { Text("检查更新") }

                UpdateViewModel.State.Checking ->
                    Text("正在检查…", style = MaterialTheme.typography.bodySmall)

                UpdateViewModel.State.UpToDate ->
                    Text(
                        "已是最新版本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                is UpdateViewModel.State.Available -> {
                    Text("发现新版本 v${update.info.version}", style = MaterialTheme.typography.titleSmall)
                    update.info.notes.lineSequence().firstOrNull { it.isNotBlank() }?.let {
                        Text(
                            it.take(120),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { updateVm.downloadAndInstall(update.info) }) { Text("下载并安装") }
                }

                is UpdateViewModel.State.Downloading -> {
                    Text("正在下载 ${update.percent}%", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.fillMaxWidth().height(6.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(update.percent / 100f).height(6.dp)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }

                is UpdateViewModel.State.Failed -> {
                    Text(
                        "更新失败：${update.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row {
                        TextButton(onClick = updateVm::check) { Text("重试") }
                        TextButton(onClick = updateVm::openReleasePage) { Text("浏览器下载") }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    val restoreText = pendingRestore
    if (restoreText != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("恢复备份") },
            text = { Text("会用备份内容覆盖当前的所有记录和自建食物，确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    backupVm.restore(restoreText)
                    pendingRestore = null
                }) { Text("覆盖恢复") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun EntryLink(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun goalLabel(goal: String): String = when (goal) {
    "LOSE" -> "减脂"
    "GAIN" -> "增肌"
    else -> "维持"
}

private fun aiSubtitle(ai: AiConfig): String = when {
    !ai.isConfigured -> "未配置，填了才能拍照认菜"
    ai.visionEnabled -> "已配置 · 识图已开启"
    else -> "已配置 · 识图关着"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
