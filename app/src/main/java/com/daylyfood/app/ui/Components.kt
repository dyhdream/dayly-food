package com.daylyfood.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
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
    val over = target in 1..consumed
    val fraction = progressOf(consumed, target)
    val accent = if (over) MaterialTheme.colorScheme.error else primary

    // Spring instead of tween: the ring settles with a little overshoot, which reads as "alive".
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow),
        label = "ringFraction",
    )
    val animatedConsumed by animateIntAsState(
        targetValue = consumed,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "ringValue",
    )

    // Slow breathing glow so the ring is never completely static.
    val infinite = rememberInfiniteTransition(label = "ringGlow")
    val glow by infinite.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "glowAlpha",
    )

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
            if (animatedFraction > 0f) {
                // Soft halo under the arc.
                drawArc(
                    color = accent.copy(alpha = glow * 0.35f),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedFraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke * 2.1f, cap = StrokeCap.Round),
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(accent.copy(alpha = 0.55f), accent, accent.copy(alpha = 0.9f)),
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedFraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$animatedConsumed",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
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
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "macroBar",
    )
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
                .height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(99.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(8.dp)
                    .background(
                        Brush.horizontalGradient(listOf(color.copy(alpha = 0.72f), color)),
                        RoundedCornerShape(99.dp),
                    ),
            )
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    brush: Brush? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier
                .then(if (brush != null) Modifier.background(brush, shape) else Modifier)
                .padding(16.dp),
            content = content,
        )
    }
}

/**
 * Fades and pops a section in when it first appears, with an optional stagger delay.
 * Cheap eye candy that also hides the one-frame flash of data arriving.
 */
@Composable
fun PopIn(
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "popAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "popScale",
    )
    Box(modifier.graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }) {
        content()
    }
}

/** Warm gradient for the hero card, derived from the theme so dark mode still reads right. */
@Composable
fun heroBrush(): Brush {
    val colors = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        listOf(colors.primaryContainer.copy(alpha = 0.65f), colors.surface),
    )
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
