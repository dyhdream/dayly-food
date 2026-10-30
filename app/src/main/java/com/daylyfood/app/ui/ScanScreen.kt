package com.daylyfood.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daylyfood.app.domain.MEAL_LABELS
import com.daylyfood.app.domain.NutritionLabelParser
import com.daylyfood.app.domain.RecognizedFood
import kotlin.math.roundToInt

/**
 * Result of one shot, shown as a dialog over whatever the user was doing: a failure is just a
 * line of text, a success becomes the confirm form. No separate page in between.
 */
@Composable
fun ScanOverlay(
    uri: Uri,
    meal: String,
    day: Long,
    onDone: () -> Unit,
    onRetake: () -> Unit,
    onManualAdd: () -> Unit,
    onOpenAiSettings: () -> Unit,
    vm: ScanViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // A new photo means a new attempt; the view model keeps no state across shots.
    LaunchedEffect(uri) { vm.start(uri) }

    Dialog(
        onDismissRequest = { vm.reset(); onDone() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 620.dp),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("识别结果", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.reset(); onDone() }) { Text("关闭") }
                }

                when (val current = state) {
                    null, ScanViewModel.State.Reading -> {
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("正在识别…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    is ScanViewModel.State.LabelReady -> LabelConfirmForm(
                        label = current.label,
                        meal = meal,
                        onSave = { name, grams, chosenMeal ->
                            vm.saveLabel(name, current.label, grams, chosenMeal, day) { onDone() }
                        },
                    )

                    is ScanViewModel.State.DishesReady -> DishesConfirmForm(
                        items = current.items,
                        meal = meal,
                        onSave = { kept, chosenMeal -> vm.saveDishes(kept, chosenMeal, day) { onDone() } },
                    )

                    is ScanViewModel.State.Failed -> {
                        Text(
                            current.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = onRetake, modifier = Modifier.fillMaxWidth()) { Text("重拍一张") }
                if (current.offerAiSetup) {
                    Button(onClick = onOpenAiSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("去开启识图")
                    }
                }
                        OutlinedButton(onClick = onManualAdd, modifier = Modifier.fillMaxWidth()) {
                            Text("手动添加")
                        }
                        current.rawText?.let { text ->
                            Text(
                                "识别到的文字：\n$text",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                    "提示：营养表识别完全本地完成，不要钱；拍照认菜才会调用 AI。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
                }
            }
        }
    }
}

@Composable
private fun LabelConfirmForm(
    label: NutritionLabelParser.Label,
    meal: String,
    onSave: (name: String, grams: Double, meal: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var gramsText by remember { mutableStateOf((label.servingGrams ?: 100.0).roundToInt().toString()) }
    var chosenMeal by remember { mutableStateOf(meal) }
    val grams = gramsText.toDoubleOrNull() ?: 0.0
    val per100 = label.per100()
    val factor = grams / 100.0

    SectionCard {
        Text("营养成分表", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "每100g：${per100.kcal?.roundToInt() ?: "?"} kcal",
            style = MaterialTheme.typography.bodyMedium,
        )
        label.energyKj?.let {
            Text(
                "标签原文 ${it.roundToInt()} kJ ÷ 4.184 ≈ ${(it / 4.184).roundToInt()} kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "蛋白 ${per100.protein?.let { "${it.roundToInt()}g" } ?: "?"} · " +
                "脂肪 ${per100.fat?.let { "${it.roundToInt()}g" } ?: "?"} · " +
                "碳水 ${per100.carb?.let { "${it.roundToInt()}g" } ?: "?"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        per100.sodiumMg?.let {
            Text(
                "钠 ${it.roundToInt()} mg" + (per100.fiber?.let { f -> " · 膳食纤维 ${f.roundToInt()}g" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (label.isSuspicious()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "标签数值和三大营养素对不上，可能扫错了行，保存前请核对。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    SectionCard {
        Text("名称与份量", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("食品名，如「卫龙 大面筋」") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = gramsText,
                onValueChange = { gramsText = it },
                label = { Text("吃了多少") },
                suffix = { Text("g") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(160.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "≈ ${(per100.kcal ?: 0.0).times(factor).roundToInt()} kcal",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        MealChips(chosenMeal) { chosenMeal = it }
    }

    Button(
        onClick = { onSave(name, grams, chosenMeal) },
        enabled = grams > 0 && per100.kcal != null,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("保存到食物库并记录") }
}

private data class DishRow(val item: RecognizedFood, val included: Boolean, val grams: String)

@Composable
private fun DishesConfirmForm(
    items: List<RecognizedFood>,
    meal: String,
    onSave: (List<RecognizedFood>, String) -> Unit,
) {
    var rows by remember(items) {
        mutableStateOf(items.map { DishRow(it, true, it.grams.roundToInt().toString()) })
    }
    var chosenMeal by remember { mutableStateOf(meal) }

    val selected = rows.filter { it.included }
    val totalKcal = selected.sumOf { row ->
        val grams = row.grams.toDoubleOrNull() ?: row.item.grams
        row.item.kcal100 * grams / 100.0
    }

    SectionCard {
        Text("认出的食物", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            "一图多菜时只勾你要记的；克数是估计值，可改。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        rows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = row.included,
                    onCheckedChange = { checked ->
                        rows = rows.toMutableList().also { it[index] = row.copy(included = checked) }
                    },
                )
                Column(Modifier.weight(1f)) {
                    Text(row.item.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (row.item.isFromLibrary) "库里已有 · 每100g ${row.item.kcal100.roundToInt()} kcal"
                        else "AI 估算 · 每100g ${row.item.kcal100.roundToInt()} kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (row.item.isFromLibrary) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = row.grams,
                    onValueChange = { value ->
                        rows = rows.toMutableList().also { it[index] = row.copy(grams = value) }
                    },
                    label = { Text("克") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(92.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "合计约 ${totalKcal.roundToInt()} kcal",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        MealChips(chosenMeal) { chosenMeal = it }
    }

    Button(
        onClick = {
            val kept = selected.map { row ->
                row.item.copy(grams = row.grams.toDoubleOrNull() ?: row.item.grams)
            }
            onSave(kept, chosenMeal)
        },
        enabled = selected.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("记录这 ${selected.size} 样") }

    Text(
        "库里的食物直接引用；AI 认出的会存成「自建」，下次直接选、不再花钱。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MealChips(chosen: String, onChoose: (String) -> Unit) {
    Spacer(Modifier.height(10.dp))
    Text("记到", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MEAL_LABELS.forEach { (key, text) ->
            FilterChip(selected = chosen == key, onClick = { onChoose(key) }, label = { Text(text) })
        }
    }
}
