package com.daylyfood.app.ui

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Listens with our own UI instead of launching the vendor recognizer screen.
 *
 * The system intent shows the provider's branded panel (Google's, on phones that have it) and
 * hides our own wording; talking to SpeechRecognizer directly keeps the flow inside the app.
 */
@Composable
fun VoiceListenDialog(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var heard by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            error = "这台设备没有语音识别，只能用文字搜索"
            onDispose { }
        } else {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { error = null }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(code: Int) {
                    error = describe(code)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { heard = it }
                }

                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    finished = true
                    if (text.isNullOrBlank()) error = "没听清，再来一次" else onResult(text)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            runCatching { recognizer.startListening(intent) }
                .onFailure { error = "启动识别失败" }

            onDispose {
                runCatching { recognizer.destroy() }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (error == null) "请说吃了什么" else "没听清") },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (error == null && !finished) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    error ?: heard.ifBlank { "比如「两个包子一碗豆浆」" },
                    textAlign = TextAlign.Center,
                    style = if (heard.isBlank() && error == null) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (finished) "好" else "取消") } },
    )
}

private fun describe(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_AUDIO -> "录音出错"
    SpeechRecognizer.ERROR_CLIENT -> "识别被中断"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限"
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "识别需要网络"
    SpeechRecognizer.ERROR_NO_MATCH -> "没听清"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙，稍后再试"
    SpeechRecognizer.ERROR_SERVER -> "识别服务出错"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听到声音"
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "设备不支持中文识别"
    else -> "识别失败"
}
