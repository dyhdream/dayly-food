package com.daylyfood.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.daylyfood.app.data.db.ProfileEntity
import com.daylyfood.app.data.db.ServingEntity
import com.daylyfood.app.domain.Nutrition
import kotlin.math.roundToInt

private val MEALS = listOf(
    "breakfast" to "早餐",
    "lunch" to "午餐",
    "dinner" to "晚餐",
    "snack" to "加餐",
)

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("正在导入食物库…")
        }
    }
}

@Composable
fun ProfileScreen(
    initial: ProfileEntity?,
    onSave: (ProfileEntity) -> Unit,
    onCancel: (() -> Unit)?,
) {
    var sex by remember { mutableStateOf(initial?.sex ?: "M") }
    var age by remember { mutableStateOf(initial?.age?.toString() ?: "28") }
    var height by remember { mutableStateOf(initial?.heightCm?.toString() ?: "175") }
    var weight by remember { mutableStateOf(initial?.weightKg?.toString() ?: "70") }
    var activity by remember { mutableStateOf(initial?.activity ?: 1.375) }
    var goal by remember { mutableStateOf(initial?.goal ?: "LOSE") }

    val ageV = age.toIntOrNull() ?: 28
    val heightV = height.toDoubleOrNull() ?: 175.0
    val weightV = weight.toDoubleOrNull() ?: 70.0
    val bmr = Nutrition.bmr(sex, ageV, heightV, weightV)
    val tdee = Nutrition.tdee(bmr, activity)
    val target = Nutrition.targetKcal(tdee, goal, sex)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("个人信息", style = MaterialTheme.typography.headlineSmall)
        Text("用于计算基础代谢与每日目标，只存在本机。", style = MaterialTheme.typography.bodySmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sex == "M", onClick = { sex = "M" }, label = { Text("男") })
            FilterChip(selected = sex == "F", onClick = { sex = "F" }, label = { Text("女") })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = age, onValueChange = { age = it }, label = { Text("年龄") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = height, onValueChange = { height = it }, label = { Text("身高 cm") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = weight, onValueChange = { weight = it }, label = { Text("体重 kg") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Text("活动量", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1.2 to "久坐", 1.375 to "轻度").forEach { (v, label) ->
                FilterChip(selected = activity == v, onClick = { activity = v }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1.55 to "中度", 1.725 to "高强度", 1.9 to "极高").forEach { (v, label) ->
                FilterChip(selected = activity == v, onClick = { activity = v }, label = { Text(label) })
            }
        }

        Text("目标", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("LOSE" to "减脂", "MAINTAIN" to "维持", "GAIN" to "增肌").forEach { (v, label) ->
                FilterChip(selected = goal == v, onClick = { goal = v }, label = { Text(label) })
            }
        }

        HorizontalDivider()
        Text("基础代谢 BMR：${bmr.roundToInt()} kcal")
        Text("每日消耗 TDEE：${tdee.roundToInt()} kcal")
        Text("每日摄入目标：${target.roundToInt()} kcal", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                onSave(
                    ProfileEntity(
                        sex = sex, age = ageV, heightCm = heightV,
                        weightKg = weightV, activity = activity, goal = goal,
                    )
                )
            }) { Text("保存") }
            if (onCancel != null) TextButton(onClick = onCancel) { Text("取消") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun HomeScreen(
    onAdd: (String) -> Unit,
    onProfile: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val profile by vm.profile.collectAsStateWithLifecycle(null)
    val entries by vm.entries.collectAsStateWithLifecycle(emptyList())

    val target = profile?.let {
        Nutrition.targetKcal(Nutrition.tdee(Nutrition.bmr(it.sex, it.age, it.heightCm, it.weightKg), it.activity), it.goal, it.sex)
    } ?: 0.0
    val macros = profile?.let { Nutrition.macros(target, it.weightKg, it.goal) }

    val eaten = entries.sumOf { it.kcal }
    val protein = entries.sumOf { it.protein }
    val fat = entries.sumOf { it.fat }
    val carb = entries.sumOf { it.carb }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("每日食记", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onProfile) { Text("设置") }
        }

        Text(
            "目标 ${target.roundToInt()} · 已摄入 ${eaten.roundToInt()} · 剩余 ${(target - eaten).roundToInt()} kcal",
            style = MaterialTheme.typography.titleMedium,
        )
        if (macros != null) {
            Text(
                "蛋白 ${protein.roundToInt()}/${macros.proteinG.roundToInt()}g   " +
                    "脂肪 ${fat.roundToInt()}/${macros.fatG.roundToInt()}g   " +
                    "碳水 ${carb.roundToInt()}/${macros.carbG.roundToInt()}g",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        HorizontalDivider()

        MEALS.forEach { (key, label) ->
            val mealEntries = entries.filter { it.meal == key }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$label  ${mealEntries.sumOf { it.kcal }.roundToInt()} kcal",
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = { onAdd(key) }) { Text("＋ 添加") }
            }
            if (mealEntries.isEmpty()) {
                Text("未记录", style = MaterialTheme.typography.bodySmall)
            }
            mealEntries.forEach { entry ->
                Row(
                    Modifier.fillMaxWidth().clickable { },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(entry.nameSnapshot)
                        Text(
                            "${entry.grams.roundToInt()}g · ${entry.kcal.roundToInt()} kcal",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { vm.delete(entry.id) }) { Text("删除") }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SearchScreen(
    meal: String,
    onDone: () -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle(emptyList())
    val selected by vm.selected.collectAsStateWithLifecycle(null)
    val servings by vm.servings.collectAsStateWithLifecycle(emptyList())

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("添加到 ${MEALS.first { it.first == meal }.second}", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onDone) { Text("完成") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            label = { Text("搜索食物、菜品或别名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("共 ${results.size} 条 · 数据来自内置食物库", style = MaterialTheme.typography.bodySmall)

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            results.forEach { food ->
                FoodRow(food) { vm.select(food) }
            }
        }
    }

    val food = selected
    if (food != null) {
        ServingDialog(
            food = food,
            servings = servings,
            onDismiss = vm::dismiss,
            onConfirm = { grams -> vm.record(meal, grams) { onDone() } },
        )
    }
}

@Composable
private fun FoodRow(food: FoodEntity, onClick: () -> Unit) {
    val kindLabel = when (food.kind) {
        "DISH" -> "菜品"
        "INGREDIENT" -> "食材"
        else -> "包装"
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(food.name)
            Text(
                "$kindLabel · ${food.kcal100.roundToInt()} kcal/100${food.baseUnit}" +
                    (if (food.verified) "" else " · 待核实"),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun ServingDialog(
    food: FoodEntity,
    servings: List<ServingEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    val defaultGrams = servings.firstOrNull { it.isDefault }?.grams ?: 100.0
    var gramsText by remember(food.id) { mutableStateOf(defaultGrams.roundToInt().toString()) }
    val grams = gramsText.toDoubleOrNull() ?: defaultGrams
    val kcal = food.kcal100 * grams / 100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (servings.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        servings.take(3).forEach { s ->
                            FilterChip(
                                selected = gramsText == s.grams.roundToInt().toString(),
                                onClick = { gramsText = s.grams.roundToInt().toString() },
                                label = { Text("${s.label} ${s.grams.roundToInt()}g") },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { gramsText = it },
                    label = { Text("克数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text("约 ${kcal.roundToInt()} kcal", style = MaterialTheme.typography.titleMedium)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(grams) }) { Text("记录") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
