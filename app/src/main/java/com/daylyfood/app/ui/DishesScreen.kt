package com.daylyfood.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

/** Category filter for the browse screen; ALL is the default. */
private enum class FoodFilter(val label: String, val kind: String?) {
    ALL("默认", null),
    DISH("菜品", "DISH"),
    INGREDIENT("食材", "INGREDIENT"),
    PACKAGED("包装", "PACKAGED"),
    CUSTOM("自建", "CUSTOM"),
}

/** Browse anything: dishes, ingredients, packaged goods and self-made entries. */
@Composable
fun DishesScreen(
    onCompose: () -> Unit,
    vm: DishViewModel = hiltViewModel(),
) {
    val all by vm.allFoods.collectAsStateWithLifecycle(emptyList())
    var pending by remember { mutableStateOf<FoodEntity?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(FoodFilter.ALL) }

    fun matches(food: FoodEntity): Boolean {
        val q = query.trim()
        val byName = q.isEmpty() ||
            food.name.contains(q, ignoreCase = true) ||
            food.notes?.contains(q, ignoreCase = true) == true
        val byKind = filter.kind == null || food.kind == filter.kind
        return byName && byKind
    }

    val shown = all.filter(::matches)

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column {
                Spacer(Modifier.height(8.dp))
                Text("查找", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "菜品、食材、包装食品都在这里；点一条直接记进餐次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Button(onClick = onCompose, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("组合菜品（选食材 + 烹饪方式）")
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索名称，如香干、番茄炒蛋、可乐") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FoodFilter.entries.forEach { option ->
                    val count = all.count { f ->
                        (option.kind == null || f.kind == option.kind) &&
                            (query.isBlank() || f.name.contains(query.trim(), true))
                    }
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text("${option.label} $count") },
                    )
                }
            }
        }

        item {
            Text(
                "共 ${shown.size} 条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (shown.isEmpty()) {
            item { EmptyHint("没找到，换个说法或点上面的「组合菜品」") }
        }

        items(shown, key = { it.id }) { food ->
            DishRow(food, onClick = { pending = food }, modifier = Modifier.animateItem())
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    val dish = pending
    if (dish != null) {
        MealPickerDialog(
            title = dish.name,
            subtitle = "约 ${dish.kcal100.roundToInt()} kcal/100g",
            suggested = vm.suggestedMeal(),
            onDismiss = { pending = null },
            onPick = { meal -> vm.logDish(dish.id, meal) { pending = null } },
        )
    }
}

@Composable
private fun DishRow(dish: FoodEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dish.name, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(6.dp))
                KindBadge(dish.kind)
            }
            // Recipe detail stays out of the list; energy and macros are what you scan for.
            Text(
                "每100${dish.baseUnit} 蛋白 ${dish.protein100.roundToInt()} · " +
                    "脂肪 ${dish.fat100.roundToInt()} · 碳水 ${dish.carb100.roundToInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text("${dish.kcal100.roundToInt()}", style = MaterialTheme.typography.titleSmall)
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
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

/** Compose a dish from ingredients + a cooking method, then log it into the given slot. */
@Composable
fun ComposeDishScreen(
    meal: String,
    day: Long,
    onDone: () -> Unit,
    vm: DishViewModel = hiltViewModel(),
) {
    BackHandler(onBack = { vm.cancelCompose(); onDone() })
    val state by vm.compose.collectAsStateWithLifecycle()
    val totals = state.totals
    var chosenMeal by remember(meal) { mutableStateOf(meal) }

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
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "移除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
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
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
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
                    FilterChip(
                        selected = chosenMeal == key,
                        onClick = { chosenMeal = key },
                        label = { Text(label) },
                    )
                }
            }
        }

        Button(
            onClick = { vm.saveDish(chosenMeal, day) { onDone() } },
            enabled = state.items.isNotEmpty() && totals.grams > 0,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存为我的菜品并记录") }

        Text(
            "保存后可在「查找」里再次使用；总热量由食材和油量精确相加，每 100g 数值按整盘重量折算。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
    }
}
