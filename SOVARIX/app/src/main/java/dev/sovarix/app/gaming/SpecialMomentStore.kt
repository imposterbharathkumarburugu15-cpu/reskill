package dev.sovarix.app.gaming

import android.content.Context
import android.util.AtomicFile
import dev.sovarix.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SpecialMomentStore(context: Context) {

    private val file = AtomicFile(File(context.filesDir, "special-moments-v1.json"))

    fun load(): List<SpecialMoment> {
        if (!file.baseFile.exists()) return emptyList()
        return try {
            val root = JSONObject(file.openRead().bufferedReader().use { it.readText() })
            val array = root.optJSONArray("moments") ?: return emptyList()
            val result = mutableListOf<SpecialMoment>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val signalsArray = obj.optJSONArray("availableSignals")
                val signals = mutableListOf<String>()
                if (signalsArray != null) {
                    for (j in 0 until signalsArray.length()) {
                        signals.add(signalsArray.getString(j))
                    }
                }

                var sample: Sample? = null
                val sampleObj = obj.optJSONObject("deviceState")
                if (sampleObj != null) {
                    sample = Sample(
                        elapsedMs = sampleObj.optLong("elapsedMs"),
                        wallMs = sampleObj.optLong("wallMs"),
                        batteryPct = sampleObj.optDouble("batteryPct").takeIf { !it.isNaN() && it.isFinite() },
                        batteryC = sampleObj.optDouble("batteryC").takeIf { !it.isNaN() && it.isFinite() },
                        charging = if (sampleObj.has("charging")) sampleObj.optBoolean("charging") else null,
                        currentUa = if (sampleObj.has("currentUa")) sampleObj.optInt("currentUa") else null,
                        thermalStatus = if (sampleObj.has("thermalStatus")) sampleObj.optInt("thermalStatus") else null,
                        headroom = sampleObj.optDouble("headroom").takeIf { !it.isNaN() && it.isFinite() },
                        availableMemoryBytes = if (sampleObj.has("availableMemoryBytes")) sampleObj.optLong("availableMemoryBytes") else null,
                        totalMemoryBytes = if (sampleObj.has("totalMemoryBytes")) sampleObj.optLong("totalMemoryBytes") else null,
                        lowMemory = if (sampleObj.has("lowMemory")) sampleObj.optBoolean("lowMemory") else null,
                        appPssKb = if (sampleObj.has("appPssKb")) sampleObj.optInt("appPssKb") else null,
                        processCpuMs = sampleObj.optLong("processCpuMs", 0L),
                        powerSave = sampleObj.optBoolean("powerSave", false),
                        interactive = sampleObj.optBoolean("interactive", true),
                        workload = Workload.entries.find { it.name == sampleObj.optString("workload") } ?: Workload.GAMING,
                        collectionMs = sampleObj.optDouble("collectionMs", 0.0)
                    )
                }

                var motion: MotionSnapshot? = null
                val motionObj = obj.optJSONObject("motionSnapshot")
                if (motionObj != null) {
                    motion = MotionSnapshot(
                        timestamp = motionObj.optLong("timestamp"),
                        gyroAvailable = motionObj.optBoolean("gyroAvailable", false),
                        accelerometerAvailable = motionObj.optBoolean("accelerometerAvailable", false),
                        linearAccelerationAvailable = motionObj.optBoolean("linearAccelerationAvailable", false),
                        rotationVectorAvailable = motionObj.optBoolean("rotationVectorAvailable", false),
                        gyroX = motionObj.optDouble("gyroX", 0.0).toFloat(),
                        gyroY = motionObj.optDouble("gyroY", 0.0).toFloat(),
                        gyroZ = motionObj.optDouble("gyroZ", 0.0).toFloat(),
                        angularVelocityMagnitude = motionObj.optDouble("angularVelocityMagnitude", 0.0).toFloat(),
                        gyroSpike = motionObj.optBoolean("gyroSpike", false),
                        accelX = motionObj.optDouble("accelX", 0.0).toFloat(),
                        accelY = motionObj.optDouble("accelY", 0.0).toFloat(),
                        accelZ = motionObj.optDouble("accelZ", 0.0).toFloat(),
                        accelerationMagnitude = motionObj.optDouble("accelerationMagnitude", 0.0).toFloat(),
                        accelerationSpike = motionObj.optBoolean("accelerationSpike", false),
                        linearAccelX = motionObj.optDouble("linearAccelX", 0.0).toFloat(),
                        linearAccelY = motionObj.optDouble("linearAccelY", 0.0).toFloat(),
                        linearAccelZ = motionObj.optDouble("linearAccelZ", 0.0).toFloat(),
                        linearAccelerationMagnitude = motionObj.optDouble("linearAccelerationMagnitude", 0.0).toFloat(),
                        movementIntensity = motionObj.optDouble("movementIntensity", 0.0).toFloat(),
                        suddenRotationDetected = motionObj.optBoolean("suddenRotationDetected", false),
                        suddenMotionDetected = motionObj.optBoolean("suddenMotionDetected", false),
                        confidence = motionObj.optDouble("confidence", 0.0).toFloat()
                    )
                }

                var evidence: SignalEvidence? = null
                val evObj = obj.optJSONObject("signalEvidence")
                if (evObj != null) {
                    evidence = SignalEvidence(
                        audioConfidence = evObj.optDouble("audioConfidence", 0.0),
                        screenConfidence = evObj.optDouble("screenConfidence", 0.0),
                        motionConfidence = evObj.optDouble("motionConfidence", 0.0),
                        gyroConfidence = evObj.optDouble("gyroConfidence", 0.0),
                        accelerationConfidence = evObj.optDouble("accelerationConfidence", 0.0),
                        hapticConfidence = evObj.optDouble("hapticConfidence", 0.0),
                        performanceConfidence = evObj.optDouble("performanceConfidence", 0.0),
                        thermalConfidence = evObj.optDouble("thermalConfidence", 0.0),
                        combinedConfidence = evObj.optDouble("combinedConfidence", 0.0)
                    )
                }

                val gamePackage = obj.optString("gamePackage", "unknown.game")
                val gameName = obj.optString("gameName", "Unknown Game")
                val genre = try {
                    GameGenre.valueOf(obj.optString("gameGenre", GameGenre.UNKNOWN.name))
                } catch (e: Exception) {
                    GameGenre.UNKNOWN
                }
                val eventType = obj.optString("eventType", obj.optString("momentType", MomentType.COMBINED_GAME_EVENT))
                val clipRef = obj.optString("clipUri", obj.optString("clipReference", obj.optString("clipPath", ""))).takeIf { it.isNotBlank() }
                val thumbUri = obj.optString("thumbnailUri", "").takeIf { it.isNotBlank() }

                // Deserialize GameSpecificMetadata if present
                var specificMeta: GameSpecificMetadata? = null
                val metaObj = obj.optJSONObject("gameSpecificMetadata")
                if (metaObj != null) {
                    val metaGenreStr = metaObj.optString("genre", "")
                    specificMeta = when (metaGenreStr) {
                        GameGenre.CRICKET.name -> GameSpecificMetadata.Cricket(
                            ballEvent = metaObj.optString("ballEvent").takeIf { it.isNotBlank() },
                            innings = if (metaObj.has("innings")) metaObj.optInt("innings") else null,
                            scoreContext = metaObj.optString("scoreContext").takeIf { it.isNotBlank() },
                            wicketContext = metaObj.optString("wicketContext").takeIf { it.isNotBlank() }
                        )
                        GameGenre.BATTLE_ROYALE.name -> GameSpecificMetadata.BattleRoyale(
                            combatIntensity = if (metaObj.has("combatIntensity")) metaObj.optDouble("combatIntensity").toFloat() else null,
                            movementIntensity = if (metaObj.has("movementIntensity")) metaObj.optDouble("movementIntensity").toFloat() else null,
                            audioEvent = metaObj.optString("audioEvent").takeIf { it.isNotBlank() }
                        )
                        GameGenre.RACING.name -> GameSpecificMetadata.Racing(
                            speedContext = if (metaObj.has("speedContext")) metaObj.optDouble("speedContext").toFloat() else null,
                            racePhase = metaObj.optString("racePhase").takeIf { it.isNotBlank() },
                            overtakeContext = metaObj.optString("overtakeContext").takeIf { it.isNotBlank() }
                        )
                        GameGenre.SPORTS.name -> GameSpecificMetadata.Sports(
                            actionContext = metaObj.optString("actionContext").takeIf { it.isNotBlank() },
                            periodContext = metaObj.optString("periodContext").takeIf { it.isNotBlank() },
                            scoreContext = metaObj.optString("scoreContext").takeIf { it.isNotBlank() }
                        )
                        else -> null
                    }
                }

                val score = obj.optInt("momentScore", (obj.optDouble("confidence", 0.75) * 100).toInt().coerceIn(0, 100))
                val durSec = obj.optDouble("duration", obj.optLong("durationMs", 12_000L) / 1000.0)

                result.add(
                    SpecialMoment(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        gamePackage = gamePackage,
                        gameName = gameName,
                        gameGenre = genre,
                        eventType = eventType,
                        confidence = obj.optDouble("confidence", 0.85),
                        momentScore = score,
                        clipUri = clipRef,
                        thumbnailUri = thumbUri,
                        duration = durSec,
                        durationMs = (durSec * 1000).toLong(),
                        clipReference = clipRef,
                        audioEvidence = obj.optDouble("audioEvidence", 0.0),
                        visualEvidence = obj.optDouble("visualEvidence", 0.0),
                        motionEvidence = obj.optDouble("motionEvidence", 0.0),
                        hapticEvidence = obj.optDouble("hapticEvidence", 0.0),
                        twinStateSnapshot = sample,
                        battery = if (obj.has("battery")) obj.optDouble("battery") else (if (obj.has("batteryState")) obj.optDouble("batteryState") else sample?.batteryC),
                        temperature = if (obj.has("temperature")) obj.optDouble("temperature") else sample?.batteryC,
                        thermalState = if (obj.has("thermalState")) obj.optInt("thermalState") else sample?.thermalStatus,
                        memoryPressure = if (obj.has("memoryPressure")) obj.optBoolean("memoryPressure") else sample?.lowMemory,
                        motionMetrics = obj.optString("motionMetrics").takeIf { it.isNotBlank() },
                        audioMetrics = obj.optString("audioMetrics").takeIf { it.isNotBlank() },
                        performanceMetrics = obj.optString("performanceMetrics").takeIf { it.isNotBlank() },
                        performanceState = obj.optString("performanceState").takeIf { it.isNotBlank() },
                        predictionContext = null,
                        gameSpecificMetadata = specificMeta,
                        availableSignals = signals,
                        motionSnapshot = motion,
                        signalEvidence = evidence,
                        notes = obj.optString("notes", "")
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(moments: List<SpecialMoment>) {
        val root = JSONObject()
        val array = JSONArray()

        moments.forEach { m ->
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("timestamp", m.timestamp)
            obj.put("duration", m.duration)
            obj.put("durationMs", m.durationMs)
            obj.put("gamePackage", m.gamePackage)
            obj.put("gameName", m.gameName)
            obj.put("gameGenre", m.gameGenre.name)
            obj.put("eventType", m.eventType)
            obj.put("momentType", m.momentType) // backward compatibility
            obj.put("momentScore", m.momentScore)
            obj.put("confidence", m.confidence)
            obj.put("clipUri", m.clipUri ?: JSONObject.NULL)
            obj.put("thumbnailUri", m.thumbnailUri ?: JSONObject.NULL)
            obj.put("clipReference", m.clipReference ?: JSONObject.NULL)
            obj.put("clipPath", m.clipPath ?: JSONObject.NULL) // backward compatibility
            obj.put("audioEvidence", m.audioEvidence)
            obj.put("visualEvidence", m.visualEvidence)
            obj.put("motionEvidence", m.motionEvidence)
            obj.put("hapticEvidence", m.hapticEvidence)
            obj.put("battery", m.battery ?: JSONObject.NULL)
            obj.put("temperature", m.temperature ?: JSONObject.NULL)
            obj.put("thermalState", m.thermalState ?: JSONObject.NULL)
            obj.put("batteryState", m.batteryState ?: JSONObject.NULL)
            obj.put("memoryPressure", m.memoryPressure ?: JSONObject.NULL)
            obj.put("motionMetrics", m.motionMetrics ?: JSONObject.NULL)
            obj.put("audioMetrics", m.audioMetrics ?: JSONObject.NULL)
            obj.put("performanceMetrics", m.performanceMetrics ?: JSONObject.NULL)
            obj.put("performanceState", m.performanceState ?: JSONObject.NULL)
            obj.put("availableSignals", JSONArray(m.availableSignals))
            obj.put("notes", m.notes)

            // Optional game-specific metadata
            m.gameSpecificMetadata?.let { meta ->
                val metaObj = JSONObject()
                metaObj.put("genre", meta.genre.name)
                when (meta) {
                    is GameSpecificMetadata.Cricket -> {
                        metaObj.put("ballEvent", meta.ballEvent ?: JSONObject.NULL)
                        metaObj.put("innings", meta.innings ?: JSONObject.NULL)
                        metaObj.put("scoreContext", meta.scoreContext ?: JSONObject.NULL)
                        metaObj.put("wicketContext", meta.wicketContext ?: JSONObject.NULL)
                    }
                    is GameSpecificMetadata.BattleRoyale -> {
                        metaObj.put("combatIntensity", meta.combatIntensity ?: JSONObject.NULL)
                        metaObj.put("movementIntensity", meta.movementIntensity ?: JSONObject.NULL)
                        metaObj.put("audioEvent", meta.audioEvent ?: JSONObject.NULL)
                    }
                    is GameSpecificMetadata.Racing -> {
                        metaObj.put("speedContext", meta.speedContext ?: JSONObject.NULL)
                        metaObj.put("racePhase", meta.racePhase ?: JSONObject.NULL)
                        metaObj.put("overtakeContext", meta.overtakeContext ?: JSONObject.NULL)
                    }
                    is GameSpecificMetadata.Sports -> {
                        metaObj.put("actionContext", meta.actionContext ?: JSONObject.NULL)
                        metaObj.put("periodContext", meta.periodContext ?: JSONObject.NULL)
                        metaObj.put("scoreContext", meta.scoreContext ?: JSONObject.NULL)
                    }
                    is GameSpecificMetadata.Generic -> {
                        val customObj = JSONObject()
                        meta.customData.forEach { (k, v) -> customObj.put(k, v) }
                        metaObj.put("customData", customObj)
                    }
                }
                obj.put("gameSpecificMetadata", metaObj)
            }

            m.twinStateSnapshot?.let { s ->
                val sampleObj = JSONObject()
                sampleObj.put("elapsedMs", s.elapsedMs)
                sampleObj.put("wallMs", s.wallMs)
                sampleObj.put("batteryPct", s.batteryPct ?: JSONObject.NULL)
                sampleObj.put("batteryC", s.batteryC ?: JSONObject.NULL)
                sampleObj.put("charging", s.charging ?: JSONObject.NULL)
                sampleObj.put("currentUa", s.currentUa ?: JSONObject.NULL)
                sampleObj.put("thermalStatus", s.thermalStatus ?: JSONObject.NULL)
                sampleObj.put("headroom", s.headroom ?: JSONObject.NULL)
                sampleObj.put("availableMemoryBytes", s.availableMemoryBytes ?: JSONObject.NULL)
                sampleObj.put("totalMemoryBytes", s.totalMemoryBytes ?: JSONObject.NULL)
                sampleObj.put("lowMemory", s.lowMemory ?: JSONObject.NULL)
                sampleObj.put("appPssKb", s.appPssKb ?: JSONObject.NULL)
                sampleObj.put("processCpuMs", s.processCpuMs)
                sampleObj.put("powerSave", s.powerSave)
                sampleObj.put("interactive", s.interactive)
                sampleObj.put("workload", s.workload.name)
                sampleObj.put("collectionMs", s.collectionMs)
                obj.put("deviceState", sampleObj)
            }

            m.motionSnapshot?.let { mot ->
                val motObj = JSONObject()
                motObj.put("timestamp", mot.timestamp)
                motObj.put("gyroAvailable", mot.gyroAvailable)
                motObj.put("accelerometerAvailable", mot.accelerometerAvailable)
                motObj.put("linearAccelerationAvailable", mot.linearAccelerationAvailable)
                motObj.put("rotationVectorAvailable", mot.rotationVectorAvailable)
                motObj.put("gyroX", mot.gyroX)
                motObj.put("gyroY", mot.gyroY)
                motObj.put("gyroZ", mot.gyroZ)
                motObj.put("angularVelocityMagnitude", mot.angularVelocityMagnitude)
                motObj.put("gyroSpike", mot.gyroSpike)
                motObj.put("accelX", mot.accelX)
                motObj.put("accelY", mot.accelY)
                motObj.put("accelZ", mot.accelZ)
                motObj.put("accelerationMagnitude", mot.accelerationMagnitude)
                motObj.put("accelerationSpike", mot.accelerationSpike)
                motObj.put("linearAccelX", mot.linearAccelX)
                motObj.put("linearAccelY", mot.linearAccelY)
                motObj.put("linearAccelZ", mot.linearAccelZ)
                motObj.put("linearAccelerationMagnitude", mot.linearAccelerationMagnitude)
                motObj.put("movementIntensity", mot.movementIntensity)
                motObj.put("suddenRotationDetected", mot.suddenRotationDetected)
                motObj.put("suddenMotionDetected", mot.suddenMotionDetected)
                motObj.put("confidence", mot.confidence)
                obj.put("motionSnapshot", motObj)
            }

            m.signalEvidence?.let { ev ->
                val evObj = JSONObject()
                evObj.put("audioConfidence", ev.audioConfidence)
                evObj.put("screenConfidence", ev.screenConfidence)
                evObj.put("motionConfidence", ev.motionConfidence)
                evObj.put("gyroConfidence", ev.gyroConfidence)
                evObj.put("accelerationConfidence", ev.accelerationConfidence)
                evObj.put("hapticConfidence", ev.hapticConfidence)
                evObj.put("performanceConfidence", ev.performanceConfidence)
                evObj.put("thermalConfidence", ev.thermalConfidence)
                evObj.put("combinedConfidence", ev.combinedConfidence)
                obj.put("signalEvidence", evObj)
            }

            array.put(obj)
        }

        root.put("moments", array)

        val out = file.startWrite()
        try {
            out.write(root.toString(2).toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }

    fun delete(momentId: String) {
        val current = load().filterNot { it.id == momentId }
        save(current)
    }

    fun clear() {
        file.delete()
    }
}
