package com.daylyfood.app.ui

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign

/**
 * Press and hold to talk, release to finish — no separate listening screen.
 *
 * Holding is the whole interaction, so the transcript and the errors live right under the button
 * where the user is already looking; a full-screen layer would only hide the list behind it.
 */
@Composable
fun HoldToTalkBar(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun stopListening() {
        listening = false
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    fun startListening() {
        error = null
        heard = ""
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            error = "这台设备没有语音识别，用文字搜索吧"
            return
        }
        val created = SpeechRecognizer.createSpeechRecognizer(context)
        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(code: Int) {
                error = describeRecognitionError(code)
                stopListening()
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
                stopListening()
                if (text.isNullOrBlank()) error = "没听清，再按住说一次" else onResult(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer = created
        listening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { created.startListening(intent) }
            .onFailure {
                error = "麦克风打不开"
                stopListening()
            }
    }

    Column(modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { },
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            startListening()
                            // Wait for the finger to lift; recognition ends on release.
                            tryAwaitRelease()
                            if (listening) stopListening()
                        },
                    )
                },
        ) {
            Text(if (listening) "松手结束" else "按住说话，比如「两个包子一碗豆浆」")
        }
        val message = error ?: heard.takeIf { it.isNotBlank() }
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun describeRecognitionError(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_AUDIO -> "录音出错"
    SpeechRecognizer.ERROR_CLIENT -> "识别被中断"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限"
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "识别需要网络"
    SpeechRecognizer.ERROR_NO_MATCH -> "没听清"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙，稍后再试"
    SpeechRecognizer.ERROR_SERVER -> "识别服务出错"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听到声音，按住多说一会儿"
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "设备不支持中文识别"
    else -> "识别失败"
}
