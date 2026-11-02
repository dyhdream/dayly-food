package com.daylyfood.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Shows the AI-built composition before it is stored.
 *
 * The user is the last line of defence against a wrong recipe, so the ingredient list, the amounts
 * and anything the library was missing are all visible before anything is written to the diary.
 */
@Composable
fun DishRecipeDialog(
    state: DishRecipeViewModel.State,
    onConfirmAndRecord: () -> Unit,
    onConfirmOnly: () -> Unit,
    onRetry: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is DishRecipeViewModel.State.Idle -> Unit

        is DishRecipeViewModel.State.Working -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("正在整理「${state.dish}」") },
            text = { Text("用食材库里的配料算营养，大概需要几秒。") },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )

        is DishRecipeViewModel.State.Ready -> {
            val preview = state.preview
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(preview.dish.name) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "${preview.dish.kcal100.roundToInt()} kcal/100g · 一份约 ${preview.totalGrams.roundToInt()}g",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(preview.recipeText, style = MaterialTheme.typography.bodySmall)
                        if (preview.missing.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "库里没有、已跳过：${preview.missing.joinToString("、")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "所以这道菜算出来的热量偏低，介意的可以换一种说法再来一次。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (preview.sources == 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "没搜到参考做法，这份配料是按常见搭配估的，建议核对一下。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "配料参照了 ${preview.sources} 篇网上的做法。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "营养按上面的配料和克数推导，标注为 AI 配方，不是你手填的数。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = { TextButton(onClick = onConfirmAndRecord) { Text("加入并记录") } },
                dismissButton = {
                    Column {
                        TextButton(onClick = onConfirmOnly) { Text("只加入库") }
                        TextButton(onClick = onDismiss) { Text("都不要") }
                    }
                },
            )
        }

        is DishRecipeViewModel.State.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("没能整理出「${state.dish}」") },
            text = {
                Text("${state.message}。可以换个说法再试，或者用「组合菜品」自己配。")
            },
            confirmButton = { TextButton(onClick = { onRetry(state.dish) }) { Text("再试一次") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("算了") } },
        )

        is DishRecipeViewModel.State.Saved -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("已加入食物库") },
            text = {
                Text(
                    if (state.recorded) "${state.name} 已记进这一餐。" else "${state.name} 已加入库，下次能直接搜到。",
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("好") } },
        )
    }
}
