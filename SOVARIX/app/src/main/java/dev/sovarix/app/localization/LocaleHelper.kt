package dev.sovarix.app.localization

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Data model for language options in the SOVARIX language selector.
 */
data class SupportedLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String
) {
    val displayName: String get() = if (nativeName.equals(englishName, ignoreCase = true)) englishName else "$nativeName ($englishName)"
}

/**
 * Section 34: Locale management and persistence for SOVARIX.
 * Supports dynamic language switching across English, Telugu, and Hindi,
 * with an extensible architecture for future Indian regional languages.
 */
object LocaleHelper {

    private const val PREFS_NAME = "sovarix_locale_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    /** Initial supported languages */
    val SUPPORTED_LANGUAGES = listOf(
        SupportedLanguage("en", "English", "English"),
        SupportedLanguage("te", "తెలుగు", "Telugu"),
        SupportedLanguage("hi", "हिन्दी", "Hindi")
    )

    /** Future extensible Indian languages roadmap */
    val FUTURE_LANGUAGES = listOf(
        SupportedLanguage("ta", "தமிழ்", "Tamil"),
        SupportedLanguage("kn", "ಕನ್ನಡ", "Kannada"),
        SupportedLanguage("ml", "മലയാളം", "Malayalam"),
        SupportedLanguage("mr", "मराठी", "Marathi"),
        SupportedLanguage("bn", "বাংলা", "Bengali"),
        SupportedLanguage("gu", "ગુજરાતી", "Gujarati"),
        SupportedLanguage("pa", "ਪੰਜਾਬੀ", "Punjabi"),
        SupportedLanguage("or", "ଓଡ଼ିଆ", "Odia")
    )

    fun getPersistedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, null) ?: "en"
    }

    fun persistLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    /**
     * Creates a Configuration context for the specified language tag without restarting the process.
     */
    fun createLocalizedContext(context: Context, languageCode: String): Context {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        // API 33+ Per-App Language support
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            runCatching {
                localeManager?.applicationLocales = LocaleList.forLanguageTags(languageCode)
            }
        }

        return context.createConfigurationContext(config)
    }
}
