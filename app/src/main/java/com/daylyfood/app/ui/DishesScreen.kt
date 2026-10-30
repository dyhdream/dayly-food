package com.daylyfood.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daylyfood.app.data.db.FoodEntity
import com.daylyfood.app.domain.CookingMethods
import com.daylyfood.app.domain.MEAL_LABELS
import kotlin.math.roundToInt

/** Browse built-in dishes plus the ones the user composed, and log either in one tap. */
@Composable
fun DishesScreen(
    onCompose: () -> Unit,
    vm: DishViewModel = hiltViewModel(),
) {
    val mine by vm.myDishes.collectAsStateWithLifecycle(emptyList())
    val builtIn by vm.builtInDishes.collectAsStateWithLifecycle(emptyList())
    var pending by remember { mutableStateOf<FoodEntity?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("菜品", style = MaterialTheme.typography.headlineSmall)
        Text(
            "点一道菜直接记进餐次；也可以自己拼一道",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(onClick = onCompose, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("组合菜品（选食材 + 烹饪方式）")
        }

        SectionCard {
            Text("我的菜品 ${mine.size}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (mine.isEmpty()) {
                EmptyHint("还没有。点上面的按钮，用食材拼出你常吃的那道菜")
            }
            mine.forEach { dish ->
                DishRow(dish) { pending = dish }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }

        SectionCard {
            Text("内置菜品 ${builtIn.size}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            builtIn.forEach { dish ->
                DishRow(dish) { pending = dish }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    val dish = pending
    if (dish != null) {
        MealPickerDialog(
            title = dish.name,
            subtitle = "约 ${dish.kcal100.roundToInt()} kcal/100g",
            suggested = vm.suggestedMeal(),
            onDismiss = { pending = null },
            onPick = { meal ->
                vm.logDish(dish.id, meal) { pending = null }
            },
        )
    }
}

@Composable
private fun DishRow(dish: FoodEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(dish.name, style = MaterialTheme.typography.bodyMedium)
            val subtitle = dish.notes?.takeIf { it.isNotBlank() }
                ?: "每100g ${dish.kcal100.roundToInt()} kcal"
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            "${dish.kcal100.roundToInt()}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            " kcal",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun MealPickerDialog(
    title: String,
    subtitle: String,
    suggested: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var meal by remember(title) { mutableStateOf(suggested) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("记到哪一餐？", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MEAL_LABELS.forEach { (key, label) ->
                        FilterChip(
                            selected = meal == key,
                            onClick = { meal = key },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(meal) }) { Text("记录") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** Compose a dish from ingredients + a cooking method, then log it. */
@Composable
fun ComposeDishScreen(
    onDone: () -> Unit,
    vm: DishViewModel = hiltViewModel(),
) {
    val state by vm.compose.collectAsStateWithLifecycle()
    val totals = state.totals
    var meal by remember { mutableStateOf(vm.suggestedMeal()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.cancelCompose(); onDone() }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "返回")
            }
            Text("组合菜品", style = MaterialTheme.typography.headlineSmall)
        }

        OutlinedTextField(
            value = state.name,
            onValueChange = vm::setName,
            label = { Text("菜名，如「香干炒肉」") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionCard {
            Text("食材 ${state.items.size}", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (state.items.isEmpty()) {
                EmptyHint("先搜索并加入食材")
            }
            state.items.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = item.grams.roundToInt().toString(),
                        onValueChange = { vm.setGrams(item.foodId, it) },
                        label = { Text("克") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(96.dp),
                    )
                    IconButton(onClick = { vm.removeIngredient(item.foodId) }) {
                        Icon(Icons.Default.Delete, contentDescription = "移除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text("搜索食材") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.results.forEach { food ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.addIngredient(food) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(food.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "每100${food.baseUnit} ${food.kcal100.roundToInt()} kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Default.Add, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }

        SectionCard {
            Text("烹饪方式", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CookingMethods.ALL.forEach { method ->
                    FilterChip(
                        selected = state.methodId == method.id,
                        onClick = { vm.setMethod(method.id) },
                        label = { Text(method.label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.oilGrams.roundToInt().toString(),
                    onValueChange = vm::setOil,
                    label = { Text("用油") },
                    suffix = { Text("g") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(140.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "按「${CookingMethods.byId(state.methodId).label}」估算，可改",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard {
            Text("合计", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    "${totals.kcal.roundToInt()}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(" kcal", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    "整盘约 ${totals.grams.roundToInt()} g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "蛋白 ${totals.protein.roundToInt()}g · 脂肪 ${totals.fat.roundToInt()}g · 碳水 ${totals.carb.roundToInt()}g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text("记到", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MEAL_LABELS.forEach { (key, label) ->
                    FilterChip(selected = meal == key, onClick = { meal = key }, label = { Text(label) })
                }
            }
        }

        Button(
            onClick = { vm.saveDish(meal) { onDone() } },
            enabled = state.items.isNotEmpty() && totals.grams > 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存为我的菜品并记录") }

        Text(
            "保存后可在「菜品 - 我的菜品」里再次使用；总热量由食材和油量精确相加，每 100g 数值按整盘重量折算。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
    }
}
