package com.daylyfood.app.ui

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
 * Push-to-talk button. The press is handled here — no Material clickable in the way — so the
 * moment a finger lands the button reacts and the recogniser starts.
 */
@Composable
fun VoiceHoldButton(
    onResult: (String) -> Unit,
    onState: (VoiceUiState?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableStateOf(0f) }
    // Some vendor recognisers answer codes we cannot handle. After one failure the next press
    // opens the system voice panel instead — uglier, but it is the path that always works.
    var useSystemPanel by remember { mutableStateOf(false) }
    val systemPanel = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (spoken.isNullOrBlank()) {
            error = "没听清，再说一次"
        } else {
            useSystemPanel = false
            onResult(spoken)
        }
    }

    // Without this the recogniser answers ERROR_CLIENT and the user only sees "识别被中断".
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) error = "没有麦克风权限，按住时请选「允许」"
    }
    fun hasMic(): Boolean = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.RECORD_AUDIO,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    // Creating and destroying a recogniser on every press is what produced ERROR_CLIENT:
    // the service connection is torn down before it is ready. One instance, reused.
    val sharedRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        } else {
            null
        }
    }

    fun start() {
        error = null
        heard = ""
        level = 0f
        pressed = true
        if (useSystemPanel) {
            pressed = false
            runCatching {
                systemPanel.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "说出这餐吃了什么")
                    },
                )
            }.onFailure { error = "这台设备没有可用的语音识别" }
            return
        }
        if (!hasMic()) {
            pressed = false
            error = null
            permission.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            error = "这台设备没有语音识别，用文字搜索吧"
            return
        }
        val created = sharedRecognizer
        if (created == null) {
            pressed = false
            error = "这台设备没有语音识别，用文字搜索吧"
            return
        }
        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { level = 0.12f }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                level = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(code: Int) {
                listening = false
                finishing = false
                pressed = false
                val described = describeVoiceError(code)
                error = if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    described
                } else {
                    useSystemPanel = true
                    "$described，下次按住改用系统语音"
                }
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
                pressed = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) error = "没听清，再按住说一次" else onResult(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        listening = true
        finishing = false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { created.startListening(intent) }.onFailure {
            listening = false
            pressed = false
            error = "麦克风打不开：${it.message ?: it.javaClass.simpleName}"
        }
    }

    fun release() {
        pressed = false
        if (!listening) return
        listening = false
        finishing = true
        runCatching { sharedRecognizer?.stopListening() }
    }

    // Errors are a nudge, not a permanent label: clear them so the next attempt starts clean.
    LaunchedEffect(error) {
        if (error != null) {
            kotlinx.coroutines.delay(4_000)
            error = null
        }
    }

    LaunchedEffect(listening, finishing, level, heard, error) {
        onState(
            if (listening || finishing || error != null) VoiceUiState(level, heard, listening, error) else null,
        )
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { sharedRecognizer?.destroy() } }
    }

    val scale by animateFloatAsState(if (pressed) 1.12f else 1f, label = "micScale")
    Box(
        modifier
            .size(56.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (pressed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    start()
                    waitForUpOrCancellation()
                    release()
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
    SpeechRecognizer.ERROR_CLIENT -> "识别被中断，请再按住说一次"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限"
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "识别需要网络"
    SpeechRecognizer.ERROR_NO_MATCH -> "没听清，再说一次"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙，稍后再试"
    SpeechRecognizer.ERROR_SERVER -> "识别服务出错"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听到声音，按住多说一会儿"
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "设备不支持中文识别"
    else -> "识别失败（错误码 $code）"
}
