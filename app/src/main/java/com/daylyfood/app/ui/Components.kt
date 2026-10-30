package com.daylyfood.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** Clamped 0..1 progress; a zero/negative target yields 0 rather than NaN or infinity. */
internal fun progressOf(current: Number, target: Number): Float {
    val t = target.toDouble()
    if (t <= 0.0) return 0f
    return (current.toDouble() / t).toFloat().coerceIn(0f, 1f)
}

/** Circular energy gauge: consumed vs target, with the remaining/over amount in the middle. */
@Composable
fun CalorieRing(
    consumed: Int,
    target: Int,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val primarySoft = MaterialTheme.colorScheme.primaryContainer
    val over = target in 1..consumed
    val fraction = progressOf(consumed, target)
    val accent = if (over) MaterialTheme.colorScheme.error else primary

    Box(modifier = modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = surface,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (fraction > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(primarySoft, accent, accent)),
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$consumed",
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
            )
            Text(
                text = "/ $target kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            val remaining = target - consumed
            Text(
                text = if (remaining >= 0) "还可吃 $remaining" else "超出 ${-remaining}",
                style = MaterialTheme.typography.titleSmall,
                color = if (remaining >= 0) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Thin labelled bar for one macro nutrient. */
@Composable
fun MacroBar(
    label: String,
    current: Double,
    target: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val fraction = progressOf(current, target)
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(
                "${current.toInt()} / ${target.toInt()} g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(99.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(7.dp)
                    .background(color, RoundedCornerShape(99.dp)),
            )
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** Small pill showing what kind of entry this is. */
@Composable
fun KindBadge(kind: String) {
    val (text, container) = when (kind) {
        "CUSTOM" -> "自建" to MaterialTheme.colorScheme.secondaryContainer
        "DISH" -> "菜品" to MaterialTheme.colorScheme.primaryContainer
        "INGREDIENT" -> "食材" to MaterialTheme.colorScheme.surfaceVariant
        else -> "包装" to MaterialTheme.colorScheme.surfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(container, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
fun HSpacer(width: Int) = Spacer(Modifier.width(width.dp))
