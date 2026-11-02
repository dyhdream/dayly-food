package com.daylyfood.app.data.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max

/**
 * Offline Chinese speech recognition with the model that ships inside the APK.
 *
 * No system recogniser, no language pack, no network: that is what broke voice logging on phones
 * whose vendor engine refused to start. Audio is captured here and decoded by sherpa-onnx.
 */
class SherpaAsrEngine(private val context: Context) {

    private val sampleRate = 16_000
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var cached: OnlineRecognizer? = null

    /** Loads the model once; the first call takes a moment, later calls are free. */
    private fun recognizer(): OnlineRecognizer {
        cached?.let { return it }
        val modelDir = "asr"
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = sampleRate, featureDim = 80),
            modelConfig = OnlineModelConfig(
                // Positional: this class keeps no parameter names for Kotlin to match.
                transducer = OnlineTransducerModelConfig(
                    "$modelDir/encoder-epoch-99-avg-1.int8.onnx",
                    "$modelDir/decoder-epoch-99-avg-1.int8.onnx",
                    "$modelDir/joiner-epoch-99-avg-1.int8.onnx",
                ),
                tokens = "$modelDir/tokens.txt",
                numThreads = 2,
                modelType = "zipformer",
            ),
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.4f, 0f),
                rule2 = EndpointRule(true, 1.2f, 0f),
                rule3 = EndpointRule(false, 20f, 0f),
            ),
            enableEndpoint = false,
            decodingMethod = "greedy_search",
        )
        val created = OnlineRecognizer(assetManager = context.assets, config = config)
        cached = created
        return created
    }

    /**
     * Records until [stop] is called, reporting a 0..1 level and the text decoded so far.
     * Returns the final text (empty when nothing was recognised).
     */
    fun start(
        onLevel: (Float) -> Unit,
        onPartial: (String) -> Unit,
        onDone: (Result<String>) -> Unit,
    ) {
        if (running.getAndSet(true)) return
        worker = thread(name = "voice-asr", isDaemon = true) {
            try {
                val recognizer = recognizer()
                val stream = recognizer.createStream()
                val minBuffer = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(sampleRate / 5 * 2)
                val record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer * 2,
                )
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    running.set(false)
                    onDone(Result.failure(IllegalStateException("麦克风被占用，稍后再试")))
                    return@thread
                }
                record.startRecording()
                val buffer = ShortArray(minBuffer / 2)
                var text = ""
                try {
                    while (running.get()) {
                        val read = record.read(buffer, 0, buffer.size)
                        if (read <= 0) continue
                        val samples = FloatArray(read)
                        var peak = 0f
                        for (i in 0 until read) {
                            val value = buffer[i] / 32768f
                            samples[i] = value
                            peak = max(peak, abs(value))
                        }
                        onLevel(peak.coerceIn(0f, 1f))
                        stream.acceptWaveform(samples, sampleRate)
                        while (recognizer.isReady(stream)) recognizer.decode(stream)
                        val current = recognizer.getResult(stream).text
                        if (current != text) {
                            text = current
                            onPartial(current)
                        }
                    }
                } finally {
                    runCatching { record.stop() }
                    runCatching { record.release() }
                }
                val tail = recognizer.getResult(stream).text.ifBlank { text }
                stream.release()
                onDone(Result.success(tail))
            } catch (e: Exception) {
                onDone(Result.failure(e))
            } finally {
                running.set(false)
            }
        }
    }

    fun stop() {
        running.set(false)
    }

    fun shutdown() {
        running.set(false)
        cached?.release()
        cached = null
    }
}
