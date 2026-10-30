package com.daylyfood.app.ui

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daylyfood.app.BuildConfig
import com.daylyfood.app.data.db.DiaryEntryEntity
import com.daylyfood.app.data.db.FoodEntity
import com.daylyfood.app.data.db.ProfileEntity
import com.daylyfood.app.data.db.ServingEntity
import com.daylyfood.app.domain.Nutrition
import com.daylyfood.app.domain.guessMeal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val MEALS = listOf(
    "breakfast" to "早餐",
    "lunch" to "午餐",
    "dinner" to "晚餐",
    "snack" to "加餐",
)

private val PROTEIN_COLOR = Color(0xFF5F8D4E)
private val FAT_COLOR = Color(0xFFD9A441)
private val CARB_COLOR = Color(0xFF7A6BD6)

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("正在准备食物库…", style = MaterialTheme.typography.bodyMedium)
            Text(
                "首次启动只需一次，之后离线可用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- profile

@Composable
fun ProfileScreen(
    initial: ProfileEntity?,
    onSave: (ProfileEntity) -> Unit,
    onCancel: (() -> Unit)?,
    updateVm: UpdateViewModel = hiltViewModel(),
) {
    val updateState by updateVm.state.collectAsStateWithLifecycle()

    var sex by remember { mutableStateOf(initial?.sex ?: "M") }
    var age by remember { mutableStateOf((initial?.age ?: 30).toString()) }
    var height by remember { mutableStateOf((initial?.heightCm?.roundToInt() ?: 175).toString()) }
    var weight by remember { mutableStateOf((initial?.weightKg ?: 70.0).toString()) }
    var activity by remember { mutableStateOf(initial?.activity ?: 1.375) }
    var goal by remember { mutableStateOf(initial?.goal ?: "LOSE") }

    val ageV = age.toIntOrNull() ?: 30
    val heightV = height.toDoubleOrNull() ?: 175.0
    val weightV = weight.toDoubleOrNull() ?: 70.0
    val bmr = Nutrition.bmr(sex, ageV, heightV, weightV)
    val tdee = Nutrition.tdee(bmr, activity)
    val target = Nutrition.targetKcal(tdee, goal, sex)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("个人信息", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            if (onCancel != null) TextButton(onClick = onCancel) { Text("取消") }
        }

        SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("M" to "男", "F" to "女").forEach { (v, label) ->
                    FilterChip(selected = sex == v, onClick = { sex = v }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("年龄", age, { age = it }, Modifier.weight(1f), decimal = false)
                NumberField("身高 cm", height, { height = it }, Modifier.weight(1f), decimal = false)
                NumberField("体重 kg", weight, { weight = it }, Modifier.weight(1f))
            }
        }

        SectionCard {
            Text("活动量", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(
                    1.2 to "久坐不动",
                    1.375 to "轻度活动（每周 1-3 次）",
                    1.55 to "中度活动（每周 3-5 次）",
                    1.725 to "高强度（每周 6-7 次）",
                    1.9 to "极高（体力工作）",
                ).forEach { (v, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { activity = v }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(selected = activity == v, onClick = { activity = v }, label = {})
                        Spacer(Modifier.width(10.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        SectionCard {
            Text("目标", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("LOSE" to "减脂", "MAINTAIN" to "维持", "GAIN" to "增肌").forEach { (v, label) ->
                    FilterChip(selected = goal == v, onClick = { goal = v }, label = { Text(label) })
                }
            }
        }

        SectionCard {
            Text("计算结果", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            ResultRow("基础代谢", "${bmr.roundToInt()} kcal")
            ResultRow("每日消耗", "${tdee.roundToInt()} kcal")
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("每日建议摄入", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${target.roundToInt()} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Button(
            onClick = {
                onSave(
                    ProfileEntity(
                        sex = sex, age = ageV, heightCm = heightV,
                        weightKg = weightV, activity = activity, goal = goal,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存") }

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
                    Text("已是最新版本", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                is UpdateViewModel.State.Available -> {
                    Text("发现新版本 v${update.info.version}", style = MaterialTheme.typography.titleSmall)
                    update.info.notes.lineSequence().firstOrNull { it.isNotBlank() }?.let {
                        Text(it.take(120), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier,
    )
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(
    onAdd: (String, Long) -> Unit,
    onProfile: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val profile by vm.profile.collectAsStateWithLifecycle(null)
    val entries by vm.entries.collectAsStateWithLifecycle(emptyList())
    val recent by vm.recent.collectAsStateWithLifecycle(emptyList())
    val day by vm.day.collectAsStateWithLifecycle(LocalDate.now().toEpochDay())
    var editingEntry by remember { mutableStateOf<DiaryEntryEntity?>(null) }

    val today = remember { LocalDate.now().toEpochDay() }
    val dateObj = LocalDate.ofEpochDay(day)
    val dayLabel = when (today - day) {
        0L -> "今天"
        1L -> "昨天"
        else -> dateObj.format(DateTimeFormatter.ofPattern("M月d日"))
    }
    val weekday = dateObj.format(DateTimeFormatter.ofPattern("EEEE"))

    val target = profile?.let {
        Nutrition.targetKcal(
            Nutrition.tdee(Nutrition.bmr(it.sex, it.age, it.heightCm, it.weightKg), it.activity),
            it.goal,
            it.sex,
        )
    } ?: 0.0
    val macros = profile?.let { Nutrition.macros(target, it.weightKg, it.goal) }

    val eaten = entries.sumOf { it.kcal }
    val protein = entries.sumOf { it.protein }
    val fat = entries.sumOf { it.fat }
    val carb = entries.sumOf { it.carb }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("每日食记", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "$weekday · $dayLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(onClick = onProfile) {
                Icon(Icons.Default.Settings, contentDescription = "设置")
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.shiftDay(-1) }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "前一天")
            }
            Spacer(Modifier.weight(1f))
            Text(dayLabel, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.shiftDay(1) }, enabled = day < today) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "后一天",
                    tint = if (day < today) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                )
            }
        }

        SectionCard {
            CalorieRing(
                consumed = eaten.roundToInt(),
                target = target.roundToInt(),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            if (macros != null) {
                Spacer(Modifier.height(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MacroBar("蛋白质", protein, macros.proteinG, PROTEIN_COLOR)
                    MacroBar("脂肪", fat, macros.fatG, FAT_COLOR)
                    MacroBar("碳水", carb, macros.carbG, CARB_COLOR)
                }
            }
        }

        if (recent.isNotEmpty()) {
            SectionCard {
                Text("最近吃过 · 点一下直接记", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recent.forEach { item ->
                        AssistChip(
                            onClick = { vm.quickAdd(item, mealForNow()) },
                            label = { Text("${item.name} ${item.grams.roundToInt()}g") },
                        )
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            SectionCard {
                Text("这一天还没有记录", style = MaterialTheme.typography.titleSmall)
                Text(
                    "从下面选一餐开始，或直接点「最近吃过」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (today - day >= 1) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = { vm.copyPreviousDay() }) { Text("复制前一天的记录") }
                }
            }
        }

        MEALS.forEach { (key, label) ->
            val mealEntries = entries.filter { it.meal == key }
            SectionCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${mealEntries.sumOf { it.kcal }.roundToInt()} kcal",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (mealEntries.isEmpty()) {
                    EmptyHint("还没记")
                }
                mealEntries.forEach { entry ->
                    EntryRow(
                        entry = entry,
                        onClick = { editingEntry = entry },
                        onDelete = { vm.delete(entry.id) },
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { onAdd(key, day) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加$label")
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    val editing = editingEntry
    if (editing != null) {
        EditGramsDialog(
            entry = editing,
            onDismiss = { editingEntry = null },
            onConfirm = { grams ->
                vm.updateGrams(editing.id, grams)
                editingEntry = null
            },
        )
    }
}

@Composable
private fun EntryRow(
    entry: DiaryEntryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.nameSnapshot, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${entry.grams.roundToInt()}g · 蛋白 ${entry.protein.roundToInt()}g " +
                    "脂肪 ${entry.fat.roundToInt()}g 碳水 ${entry.carb.roundToInt()}g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${entry.kcal.roundToInt()}",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(" kcal", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Guess the meal slot from the clock so quick-add needs zero decisions. */
private fun mealForNow(): String = guessMeal(LocalTime.now().hour)

// ---------------------------------------------------------------- search

@Composable
fun SearchScreen(
    meal: String,
    day: Long,
    onCompose: () -> Unit,
    onDone: () -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onDone)
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle(emptyList())
    val selected by vm.selected.collectAsStateWithLifecycle(null)
    val servings by vm.servings.collectAsStateWithLifecycle(emptyList())
    val showCustom by vm.showCustom.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "添加到${MEALS.first { it.first == meal }.second}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "共 ${results.size} 条 · 内置食物库",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("完成") }
        }

        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            label = { Text("搜索食物、菜品或别名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::openCustom, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("手动添加")
            }
            OutlinedButton(onClick = onCompose, modifier = Modifier.weight(1f)) {
                Text("组合菜品")
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (results.isEmpty()) {
                EmptyHint("没找到。换个说法，或手动添加")
            }
            results.forEach { food ->
                FoodRow(food) { vm.select(food) }
            }
        }
    }

    if (showCustom) {
        CustomFoodDialog(
            onDismiss = vm::closeCustom,
            onConfirm = { name, label, grams, kcal, protein, fat, carb ->
                vm.saveCustom(name, label, grams, kcal, protein, fat, carb, meal, day) { onDone() }
            },
        )
    }

    val food = selected
    if (food != null) {
        ServingDialog(
            food = food,
            servings = servings,
            onDismiss = vm::dismiss,
            onConfirm = { grams -> vm.record(meal, grams, day) { onDone() } },
        )
    }
}

@Composable
private fun FoodRow(food: FoodEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(food.name, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(6.dp))
                KindBadge(food.kind)
                if (!food.verified) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "待核实",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
            modifier = Modifier.size(20.dp),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

// ---------------------------------------------------------------- dialogs

@Composable
private fun EditGramsDialog(
    entry: DiaryEntryEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var text by remember(entry.id) { mutableStateOf(entry.grams.roundToInt().toString()) }
    val grams = text.toDoubleOrNull() ?: entry.grams
    val kcal = entry.kcal * grams / entry.grams

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改份量") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.nameSnapshot)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("克数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Text("约 ${kcal.roundToInt()} kcal", style = MaterialTheme.typography.titleMedium)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(grams) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CustomFoodDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String, label: String, grams: Double, kcal: Double,
        protein: Double, fat: Double, carb: Double,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("一份") }
    var grams by remember { mutableStateOf("100") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carb by remember { mutableStateOf("") }

    val gramsV = grams.toDoubleOrNull() ?: 0.0
    val kcalV = kcal.toDoubleOrNull() ?: 0.0
    val ready = name.isNotBlank() && gramsV > 0 && kcalV >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动添加食物") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text("份量") },
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(grams, { grams = it }, label = { Text("克数") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f))
                }
                OutlinedTextField(kcal, { kcal = it }, label = { Text("这一份热量 kcal") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                Text("三大营养素可不填", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(protein, { protein = it }, label = { Text("蛋白") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f))
                    OutlinedTextField(fat, { fat = it }, label = { Text("脂肪") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f))
                    OutlinedTextField(carb, { carb = it }, label = { Text("碳水") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f))
                }
                Text("保存后自动记入当前餐次，以后可搜到", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = {
                    onConfirm(
                        name, label, gramsV, kcalV,
                        protein.toDoubleOrNull() ?: 0.0,
                        fat.toDoubleOrNull() ?: 0.0,
                        carb.toDoubleOrNull() ?: 0.0,
                    )
                },
            ) { Text("保存并记录") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ServingDialog(
    food: FoodEntity,
    servings: List<ServingEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    val default = servings.firstOrNull { it.isDefault } ?: servings.firstOrNull()
    var grams by remember(food.id) {
        mutableStateOf((default?.grams ?: 100.0).roundToInt().toString())
    }
    var picked by remember(food.id) { mutableStateOf(default?.id) }
    val gramsV = grams.toDoubleOrNull() ?: 100.0
    val kcal = food.kcal100 * gramsV / 100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "每100${food.baseUnit} ${food.kcal100.roundToInt()} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    servings.forEach { serving ->
                        FilterChip(
                            selected = picked == serving.id,
                            onClick = {
                                picked = serving.id
                                grams = serving.grams.roundToInt().toString()
                            },
                            label = { Text("${serving.label} ${serving.grams.roundToInt()}g") },
                        )
                    }
                }
                OutlinedTextField(
                    value = grams,
                    onValueChange = { grams = it; picked = null },
                    label = { Text("克数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${kcal.roundToInt()} kcal", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "蛋白 ${(food.protein100 * gramsV / 100).roundToInt()}g · " +
                            "脂肪 ${(food.fat100 * gramsV / 100).roundToInt()}g · " +
                            "碳水 ${(food.carb100 * gramsV / 100).roundToInt()}g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(gramsV) }) { Text("记录") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
