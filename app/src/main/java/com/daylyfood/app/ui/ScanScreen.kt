package com.daylyfood.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daylyfood.app.data.ocr.TempPhotoStore
import com.daylyfood.app.domain.MEAL_LABELS
import com.daylyfood.app.domain.NutritionLabelParser
import kotlin.math.roundToInt

/**
 * Photo entry. Today it reads nutrition labels on device (free, offline, no API key);
 * dish recognition through the vision model plugs in at the same entry point.
 */
@Composable
fun ScanScreen(
    meal: String,
    day: Long,
    onDone: () -> Unit,
    onManualAdd: () -> Unit,
    vm: ScanViewModel = hiltViewModel(),
) {
    BackHandler(onBack = { vm.reset(); onDone() })
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val photoStore = remember { TempPhotoStore(context) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.scan(uri)
    }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) vm.scan(uri) else vm.reset()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.reset(); onDone() }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "返回")
            }
            Text("拍照记一笔", style = MaterialTheme.typography.headlineSmall)
        }

        when (val current = state) {
            ScanViewModel.State.Idle, is ScanViewModel.State.Failed -> {
                Text(
                    "对准包装背面的「营养成分表」，表格占满画面、避开发光。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        val file = photoStore.newFile()
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        cameraUri = uri
                        camera.launch(uri)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("拍照扫营养成分表") }
                OutlinedButton(
                    onClick = {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("从相册选一张") }

                if (current is ScanViewModel.State.Failed) {
                    Text(
                        current.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Text(
                    "营养表识别完全在手机本地完成：不联网、不要 API Key、不花钱。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                TextButton(onClick = onManualAdd) { Text("没有营养表？手动添加") }
            }

            ScanViewModel.State.Reading -> {
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("正在本地识别…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            is ScanViewModel.State.Ready -> LabelConfirmForm(
                label = current.label,
                meal = meal,
                onSave = { name, grams, chosenMeal ->
                    vm.save(name, current.label, grams, chosenMeal, day) { onDone() }
                },
            )
        }
        Spacer(Modifier.height(20.dp))
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
        Text("识别结果", style = MaterialTheme.typography.titleSmall)
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
        Spacer(Modifier.height(10.dp))
        Text("记到", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MEAL_LABELS.forEach { (key, text) ->
                FilterChip(
                    selected = chosenMeal == key,
                    onClick = { chosenMeal = key },
                    label = { Text(text) },
                )
            }
        }
    }

    Button(
        onClick = { onSave(name, grams, chosenMeal) },
        enabled = grams > 0 && per100.kcal != null,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("保存到食物库并记录") }

    Text(
        "保存后这包食品会进「查找」，下次直接选，不用再扫。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
