package com.daylyfood.app.ui

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daylyfood.app.R
import kotlin.math.abs
import kotlin.math.max

/**
 * Hold the button itself to talk: press starts listening, release finishes.
 *
 * The panel only reports what is happening (level + words so far) — there is nothing to press
 * inside it, because the finger is already on the button.
 */
data class VoiceUiState(val level: Float, val text: String, val listening: Boolean, val error: String?)

@Composable
fun VoiceHoldButton(
    onResult: (String) -> Unit,
    onState: (VoiceUiState?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableStateOf(0f) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var listener by remember { mutableStateOf<RecognitionListener?>(null) }

    fun teardown() {
        val current = recognizer
        recognizer = null
        runCatching { current?.destroy() }
    }

    fun start() {
        error = null
        heard = ""
        level = 0f
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            error = "这台设备没有语音识别，用文字搜索吧"
            return
        }
        val created = SpeechRecognizer.createSpeechRecognizer(context)
        val cb = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { level = 0.1f }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // rmsdB is roughly -2..10; normalise so the bar reacts like a meter.
                level = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(code: Int) {
                listening = false
                finishing = false
                error = describeVoiceError(code)
                teardown()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { heard = it }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                finishing = false
                teardown()
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) error = "没听清，再按住说一次" else onResult(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        recognizer = created
        listener = cb
        created.setRecognitionListener(cb)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        listening = true
        finishing = false
        runCatching { created.startListening(intent) }.onFailure {
            listening = false
            error = "麦克风打不开"
            teardown()
        }
    }

    fun release() {
        if (!listening) return
        listening = false
        finishing = true
        runCatching { recognizer?.stopListening() }
    }

    // Report upward instead of drawing here: this composable only occupies the button's box.
    androidx.compose.runtime.LaunchedEffect(listening, finishing, level, heard, error) {
        onState(
            if (listening || finishing || error != null) {
                VoiceUiState(level, heard, listening, error)
            } else {
                null
            },
        )
    }

    DisposableEffect(Unit) { onDispose { teardown() } }

    Box(modifier) {
        FloatingActionButton(
            onClick = { /* hold, not tap */ },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        start()
                        tryAwaitRelease()
                        release()
                    },
                )
            },
        ) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = "按住说话记一餐")
        }
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
            // Centre bars react more than the edges, which reads as a voice meter.
            val weight = 1f - abs(index - bars / 2) / (bars.toFloat())
            val animated by animateFloatAsState(
                targetValue = if (active) (level * (0.4f + weight)).coerceIn(0.08f, 1f) else 0.08f,
                label = "bar$index",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(28.dp * max(0.08f, animated))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (active) 0.85f else 0.3f),
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

private fun describeVoiceError(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_AUDIO -> "录音出错"
    SpeechRecognizer.ERROR_CLIENT -> "识别被中断"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限"
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "识别需要网络"
    SpeechRecognizer.ERROR_NO_MATCH -> "没听清，再说一次"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙，稍后再试"
    SpeechRecognizer.ERROR_SERVER -> "识别服务出错"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听到声音，按住多说一会儿"
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "设备不支持中文识别"
    else -> "识别失败"
}
