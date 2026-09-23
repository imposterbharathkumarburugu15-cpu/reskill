package dev.sovarix.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.sovarix.core.SpeechOutputProvider
import dev.sovarix.core.VoiceInputProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class AndroidVoiceManager(private val context: Context) : VoiceInputProvider, SpeechOutputProvider {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _spokenText = MutableStateFlow("")
    val spokenText = _spokenText.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }
                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
            }
        }
    }

    override fun isVoiceAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    override fun startListening(
        languageTag: String,
        onResult: (spokenText: String) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        stopListening()

        if (!isVoiceAvailable()) {
            onError("Speech recognition unavailable on this device.")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

            val locale = when (languageTag.lowercase()) {
                "te" -> Locale("te", "IN")
                "hi" -> Locale("hi", "IN")
                else -> Locale.US
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }
                    override fun onError(error: Int) {
                        _isListening.value = false
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio permission required."
                            else -> "Voice recognition interrupted."
                        }
                        onError(msg)
                    }
                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            _spokenText.value = text
                            onResult(text)
                        } else {
                            onError("No speech recognized.")
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            _spokenText.value = text
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                startListening(intent)
            }
        } catch (e: Exception) {
            _isListening.value = false
            onError(e.message ?: "Failed to initialize speech recognizer")
        }
    }

    override fun stopListening() {
        _isListening.value = false
        speechRecognizer?.apply {
            stopListening()
            cancel()
            destroy()
        }
        speechRecognizer = null
    }

    override fun isSpeechAvailable(languageTag: String): Boolean {
        if (!isTtsReady || tts == null) return false
        val loc = when (languageTag.lowercase()) {
            "te" -> Locale("te", "IN")
            "hi" -> Locale("hi", "IN")
            else -> Locale.US
        }
        val availability = tts?.isLanguageAvailable(loc) ?: TextToSpeech.LANG_NOT_SUPPORTED
        return availability >= TextToSpeech.LANG_AVAILABLE
    }

    override fun speak(text: String, languageTag: String) {
        if (!isTtsReady || tts == null) return

        val loc = when (languageTag.lowercase()) {
            "te" -> Locale("te", "IN")
            "hi" -> Locale("hi", "IN")
            else -> Locale.US
        }

        tts?.language = loc
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sovarix_voice_utterance")
    }

    override fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    fun release() {
        stopListening()
        stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }
}
