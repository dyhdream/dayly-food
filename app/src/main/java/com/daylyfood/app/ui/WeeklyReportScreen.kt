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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Last seven days at a glance. Numbers come from what the user actually logged; the AI paragraph
 * is opt-in through 「每周报告」 so nobody spends quota by opening a page.
 */
@Composable
fun WeeklyReportScreen(
    onBack: () -> Unit,
    vm: WeeklyReportViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    // The view model outlives the page, so refresh on every open instead of only on first creation.
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.load() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("本周报告", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("关闭") }
        }

        if (state.loading) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.height(28.dp))
            }
            return@Column
        }

        if (state.daysLogged == 0) {
            SectionCard {
                Text("这周还没有记录", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "回「记录」页加一餐，这里就会自动出数字。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        SectionCard {
            Text("最近 7 天", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text("记录了 ${state.daysLogged} 天", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (state.target != null) {
                    "平均每天 ${state.avgKcal} kcal，目标 ${state.target} kcal"
                } else {
                    "平均每天 ${state.avgKcal} kcal（填了个人信息才能算目标）"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            Text("每天平均", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            state.rows.forEach { row ->
                Text("${row.label}：${row.value}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionCard {
            Text("这周最常吃", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            state.topFoods.forEach { (name, times) ->
                Text("$name ×$times", style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionCard {
            Text("AI 点评（可选）", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            when {
                state.aiLoading -> Text("正在想…", style = MaterialTheme.typography.bodySmall)
                state.aiText != null -> Text(state.aiText!!, style = MaterialTheme.typography.bodyMedium)
                else -> Text(
                    "看你愿不愿意多花一点额度。不点就不会调用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.aiError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = vm::askAi, enabled = !state.aiLoading) {
                Text(if (state.aiText == null) "让 AI 看看这周" else "再说一次")
            }
        }
    }
}
