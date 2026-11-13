package com.daylyfood.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Regional recipe packs. Nothing here is required for the app to work: install one when you eat
 * that way, remove it when you don't.
 */
@Composable
fun PacksScreen(
    onBack: () -> Unit,
    vm: PacksViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("地区菜品包", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("关闭") }
        }

        Text(
            "各地的家常菜不放在应用里，按需下载。装了才会有这些菜，不装不影响其他功能；不想要了可以移除。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.loading) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.height(28.dp))
            }
            return@Column
        }

        state.error?.let {
            SectionCard {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.refresh() }) { Text("重试") }
            }
            return@Column
        }

        if (state.rows.isEmpty()) {
            SectionCard {
                Text("还没有可下载的菜品包", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "之后会陆续上线各地的地方菜，不用更新应用就能下载。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        state.rows.forEach { row ->
            val installed = row.installedVersion > 0
            SectionCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.ref.name, style = MaterialTheme.typography.titleSmall)
                        if (row.ref.desc.isNotBlank()) {
                            Text(
                                row.ref.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            buildString {
                                append("${row.ref.foods} 道菜")
                                if (row.ref.bytes > 0) append(" · 约 ${(row.ref.bytes / 1024).coerceAtLeast(1)} KB")
                                if (installed) append(" · 已安装")
                                if (installed && row.installedVersion < row.ref.version) append("（有新版）")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(0.dp))
                    if (row.working) {
                        CircularProgressIndicator(Modifier.height(20.dp))
                    } else if (installed) {
                        OutlinedButton(onClick = { vm.remove(row.ref.id) }) { Text("移除") }
                    } else {
                        Button(onClick = { vm.install(row.ref.id) }) { Text("下载") }
                    }
                }
                row.error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        vm.lastMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}
