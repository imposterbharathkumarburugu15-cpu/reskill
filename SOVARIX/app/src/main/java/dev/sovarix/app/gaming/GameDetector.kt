package dev.sovarix.app.gaming

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import dev.sovarix.core.*

/**
 * 9.1 GameDetector:
 * - Detects active foreground game when legitimately possible
 * - Identifies package name and legitimate application label
 * - Manages game session lifecycle (start, end, metadata)
 * - Determines available capture capabilities
 * - Never fabricates identity: falls back to "Unknown Game" / GameGenre.UNKNOWN
 */
class GameDetector(
    private val context: Context,
    private val capabilitiesDetector: GamingCapabilitiesDetector = GamingCapabilitiesDetector(context)
) {
    private val packageManager: PackageManager = context.packageManager
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    fun hasUsagePermission(): Boolean = capabilitiesDetector.checkUsageStatsPermission()

    fun isLauncherOrSystem(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return true
        val p = packageName.lowercase()
        return p.contains("launcher") ||
               p.contains("systemui") ||
               p == "android" ||
               p.startsWith("com.android.settings") ||
               p.contains("quickstep") ||
               p.contains("homescreen")
    }

    /**
     * Determines if a package is a game legitimately via:
     * 1. Match against known GameProfileRegistry profiles
     * 2. Legitimate package keywords (Cricket, Free Fire, BGMI, COD, etc.)
     * 3. Android OS ApplicationInfo.CATEGORY_GAME flag (API 26+)
     */
    fun isGame(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName == context.packageName) return false
        if (isLauncherOrSystem(packageName)) return false

        // 1. Check known profile registry
        val profile = GameProfileRegistry.findProfile(packageName)
        if (profile.genre != GameGenre.UNKNOWN) {
            return true
        }

        // 2. Heuristic package keywords
        val lower = packageName.lowercase()
        if (lower.contains("cricket") || lower.contains("sporta") ||
            lower.contains("freefire") || lower.contains("pubg") ||
            lower.contains("bgmi") || lower.contains("cod") ||
            lower.contains("callofduty") || lower.contains("asphalt") ||
            lower.contains("konami") || lower.contains("supercell") ||
            lower.contains(".game") || lower.contains("gaming")
        ) {
            return true
        }

        // 3. Query OS package manager ApplicationInfo CATEGORY_GAME
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                appInfo.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
            } else {
                @Suppress("DEPRECATION")
                (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detects the active foreground package when legitimately possible via UsageStatsManager.
     * Returns null if usage stats are not granted or no recent foreground event occurred.
     */
    fun detectForegroundPackage(): String? {
        val usm = usageStatsManager ?: return null
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 15_000L

        return try {
            val events = usm.queryEvents(startTime, endTime) ?: return null
            var lastForegroundPackage: String? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastForegroundPackage = event.packageName
                }
            }
            lastForegroundPackage
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves game metadata from package name or user selection.
     * Cross-references GameProfileRegistry and Android PackageManager.
     * If cannot be confidently identified: gameName = "Unknown Game", genre = UNKNOWN.
     */
    fun resolveProfile(identifier: String?): GameProfile {
        if (identifier.isNullOrBlank()) {
            return GameProfile(
                gameName = "Unknown Game",
                packageName = "unknown.package",
                genre = GameGenre.UNKNOWN
            )
        }

        // 1. Check known profile registry
        val registered = GameProfileRegistry.findProfile(identifier)
        if (registered.genre != GameGenre.UNKNOWN) {
            return registered
        }

        // 2. Heuristic package keywords (e.g. Dream Cricket variants)
        val lowerId = identifier.lowercase()
        if (lowerId.contains("cricket") || lowerId.contains("sporta")) {
            val appLabel = try {
                val appInfo = packageManager.getApplicationInfo(identifier, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) { null }

            return GameProfile(
                gameName = appLabel ?: if (lowerId.contains("dream")) "Dream Cricket" else "Cricket Game",
                packageName = identifier,
                genre = GameGenre.CRICKET,
                specializedDetectors = listOf("CRICKET_SEMANTICS"),
                highLoadWorkload = false
            )
        }

        // 3. Query OS package manager for real application label if it's a package
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(identifier, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            null
        }

        return if (!appName.isNullOrBlank()) {
            val registeredByName = GameProfileRegistry.findProfile(appName)
            if (registeredByName.genre != GameGenre.UNKNOWN) {
                registeredByName.copy(packageName = identifier)
            } else {
                val isCricketName = appName.lowercase().contains("cricket")
                GameProfile(
                    gameName = appName,
                    packageName = identifier,
                    genre = if (isCricketName) GameGenre.CRICKET else GameGenre.UNKNOWN,
                    specializedDetectors = if (isCricketName) listOf("CRICKET_SEMANTICS") else emptyList(),
                    highLoadWorkload = false
                )
            }
        } else {
            // Unidentifiable package or custom game name
            GameProfile(
                gameName = if (identifier.contains(".") || identifier.isBlank()) "Unknown Game" else identifier,
                packageName = if (identifier.contains(".")) identifier else "custom.${identifier.lowercase().replace(" ", "_")}",
                genre = GameGenre.UNKNOWN
            )
        }
    }

    /**
     * Starts a new GameSession with audited capabilities and metadata.
     */
    fun startSession(identifier: String): GameSession {
        val profile = resolveProfile(identifier)
        val caps = capabilitiesDetector.detect()
        return GameSession(
            sessionId = "sess_${System.currentTimeMillis()}",
            packageName = profile.packageName,
            gameName = profile.gameName,
            sessionStart = System.currentTimeMillis(),
            captureCapabilities = caps,
            genre = profile.genre
        )
    }

    /**
     * Concludes an active GameSession.
     */
    fun endSession(session: GameSession): GameSession {
        return session.copy(sessionEnd = System.currentTimeMillis())
    }
}
