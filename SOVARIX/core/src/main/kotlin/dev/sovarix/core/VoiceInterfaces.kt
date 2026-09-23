package dev.sovarix.core

/**
 * Future-ready voice and language interaction interfaces.
 * Prepares SOVARIX for multilingual on-device speech queries without adding heavy dependencies.
 */

interface VoiceInputProvider {
    /** Returns true if on-device speech recognition is available */
    fun isVoiceAvailable(): Boolean

    /** Starts active listening for speech in the specified ISO language tag (e.g. "en", "te", "hi") */
    fun startListening(
        languageTag: String,
        onResult: (spokenText: String) -> Unit,
        onError: (errorMessage: String) -> Unit
    )

    /** Halts active speech listening */
    fun stopListening()
}

interface LanguageIntentParser {
    /** Maps natural language text in any supported language to structured LocalAIIntent */
    fun parse(query: String, languageTag: String): LocalAIIntent
}

interface SpeechOutputProvider {
    /** Returns true if text-to-speech synthesis is available for the given language */
    fun isSpeechAvailable(languageTag: String): Boolean

    /** Synthesizes spoken diagnostic explanation in the selected language */
    fun speak(text: String, languageTag: String)

    /** Immediately halts active speech output */
    fun stop()
}
