package com.daylyfood.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.daylyfood.app.R
import com.daylyfood.app.data.voice.SherpaAsrEngine
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max

/** Live feedback for the hold-to-talk button: level, words so far, or why it failed. */
data class VoiceUiState(
    val level: Float,
    val text: String,
    val listening: Boolean,
    val error: String?,
)

/**
 * Push-to-talk button backed by the bundled offline model.
 *
 * Press starts recording locally (sherpa-onnx decodes on device), release finishes and hands the
 * text back. Nothing here talks to a system recogniser, so a missing vendor engine cannot break it.
 */
@Composable
fun VoiceHoldButton(
    onResult: (String) -> Unit,
    onState: (VoiceUiState?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableStateOf(0f) }
    val engine = remember { SherpaAsrEngine(context.applicationContext) }

    LaunchedEffect(error) {
        if (error != null) {
            delay(4_000)
            error = null
        }
    }

    LaunchedEffect(listening, level, heard, error) {
        onState(
            if (listening || error != null) VoiceUiState(level, heard, listening, error) else null,
        )
    }

    DisposableEffect(Unit) { onDispose { engine.shutdown() } }

    val scale by animateFloatAsState(if (pressed) 1.12f else 1f, label = "micScale")
    Box(
        modifier
            .size(56.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (pressed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    error = null
                    heard = ""
                    listening = true
                    engine.start(
                        onLevel = { level = it },
                        onPartial = { heard = it },
                        onDone = { result ->
                            listening = false
                            pressed = false
                            result
                                .onSuccess { text ->
                                    if (text.isBlank()) error = "没听清，再按住说一次" else onResult(text)
                                }
                                .onFailure { error = it.message ?: "识别失败" }
                        },
                    )
                    waitForUpOrCancellation()
                    // Release: stop capturing; the engine reports the final text via onDone.
                    pressed = false
                    engine.stop()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_mic),
            contentDescription = "按住说话记一餐",
            tint = Color.White,
        )
    }
}

@Composable
fun Waveform(level: Float, active: Boolean) {
    val bars = 11
    Row(
        Modifier.fillMaxWidth().height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(bars) { index ->
            val weight = 1f - abs(index - bars / 2) / bars.toFloat()
            val animated by animateFloatAsState(
                targetValue = if (active) (level * (0.4f + weight)).coerceIn(0.06f, 1f) else 0.06f,
                label = "bar$index",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(28.dp * max(0.06f, animated))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (active) 0.85f else 0.3f),
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}
