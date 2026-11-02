package com.daylyfood.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Confirms what the microphone heard before anything is written.
 *
 * The list is editable because speech + default servings will sometimes be wrong, and a wrong
 * entry the user cannot remove is worse than no entry at all.
 */
@Composable
fun VoiceLogDialog(
    state: VoiceLogViewModel.State,
    onRemove: (String) -> Unit,
    onConfirm: () -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is VoiceLogViewModel.State.Idle -> Unit

        is VoiceLogViewModel.State.Resolving -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("正在听…") },
            text = { Text("把识别结果和食物库对一下。") },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )

        is VoiceLogViewModel.State.Ready -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("听到 ${state.items.size} 样") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "「${state.text}」",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    state.items.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.food.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${item.label} · ${item.food.kcal100.roundToInt()} kcal/100${item.food.baseUnit}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${item.grams.toInt()}${item.food.baseUnit}")
                            TextButton(onClick = { onRemove(item.food.id) }) { Text("删") }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "份量按默认值填，记完可以在记录里改。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onConfirm) { Text("记录") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )

        is VoiceLogViewModel.State.Empty -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("库里没有这道菜") },
            text = {
                Text("「${state.text}」里没对上食物库的名字和别名。可以搜一下，或者换种说法再说一次。")
            },
            confirmButton = { TextButton(onClick = onSearch) { Text("去搜索") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("算了") } },
        )

        is VoiceLogViewModel.State.Saved -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("记好了") },
            text = { Text("已记录 ${state.count} 项。") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("好") } },
        )
    }
}
