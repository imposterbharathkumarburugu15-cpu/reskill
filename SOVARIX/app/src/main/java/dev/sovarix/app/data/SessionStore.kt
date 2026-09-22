package dev.sovarix.app.data

import android.content.Context
import android.util.AtomicFile
import dev.sovarix.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Small atomic snapshot, checkpointed once a minute and at session end. No network. */
class SessionStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "twin-v1.json"))
    data class Loaded(val dna: DeviceDNA, val events: List<Event>, val verifications: List<Verification>, val warning: String? = null)
    fun load(): Loaded {
        if (!file.baseFile.exists()) return Loaded(DeviceDNA(), emptyList(), emptyList())
        return try {
            val root = JSONObject(file.openRead().bufferedReader().use { it.readText() })
            require(root.getInt("schemaVersion") == 1)
            val d = root.getJSONObject("dna")
            val baselines = mutableMapOf<String, Baseline>()
            val b = d.getJSONObject("baselines")
            b.keys().forEach { key -> val item = b.getJSONObject(key); baselines[key] = Baseline(item.getInt("count"), item.getDouble("meanC"), item.getDouble("m2")) }
            val biases = mutableMapOf<Int, Double>()
            val bias = d.getJSONObject("biases")
            bias.keys().forEach { key -> biases[key.toInt()] = bias.getDouble(key) }
            val dna = DeviceDNA(d.getInt("sessions"), d.getInt("observations"), baselines, biases,
                d.getInt("verifiedCount"), d.getDouble("totalAbsoluteErrorC"))
            val events = root.getJSONArray("events").objects().takeLast(200).map { Event(it.getLong("id"), it.getLong("wallMs"), it.getString("kind"), it.getString("message")) }.toMutableList()
            if (root.optBoolean("sessionWasRunning")) events += Event((events.maxOfOrNull { it.id } ?: 0) + 1,
                System.currentTimeMillis(), "INTERRUPTED", "Previous session ended without a final checkpoint. Pending comparisons were discarded; no outcome was invented.")
            val verification = root.getJSONArray("verifications").objects().takeLast(60).map { j ->
                Verification(j.getLong("id"), j.getLong("wallMs"), j.getInt("horizonMinutes"), j.number("predictedC"),
                    j.number("actualC"), j.number("signedErrorC"), j.number("predictedBattery"), j.number("actualBattery"),
                    j.getString("action"), j.getString("status"), j.getBoolean("contextChanged"), j.number("baselineTemperature"), j.optDouble("biasApplied", 0.0)) }
            Loaded(dna, events, verification)
        } catch (e: Exception) {
            Loaded(DeviceDNA(), emptyList(), emptyList(), "Saved history could not be read. ${e.javaClass.simpleName}. New observations remain available.")
        }
    }
    fun save(s: TwinState, blackBoxEvents: List<BlackBoxEvent> = emptyList()) {
        val out = file.startWrite()
        try { out.write(serialize(s, null, blackBoxEvents).toString().toByteArray()); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    fun clear() = file.delete()
    companion object {
        private fun JSONObject.putNullable(name: String, value: Any?) = put(name, value ?: JSONObject.NULL)
        fun serialize(s: TwinState, hardware: CapabilityProfile? = null, blackBoxEvents: List<BlackBoxEvent> = emptyList()): JSONObject {
            val baselines = JSONObject(); s.dna.baselineByContext.forEach { (k, b) -> baselines.put(k,
                JSONObject().put("count", b.count).put("meanC", b.meanC).put("m2", b.m2)) }
            val biases = JSONObject(); s.dna.temperatureBiasByHorizon.forEach { (k, v) -> biases.put(k.toString(), v) }
            val root = JSONObject().put("schemaVersion", 1).put("product", "SOVARIX").put("sessionWasRunning", s.running)
                .put("exportedAt", System.currentTimeMillis()).put("source", "Android public APIs; no generated samples")
                .put("limitations", "Battery temperature is not CPU temperature. Forecasts are extrapolations; scenario multipliers are assumptions. No Q-chip control or bundled LLM. CPU cost is process CPU percent of one core; battery impact is not attributable.")
                .put("dna", JSONObject().put("sessions", s.dna.sessions).put("observations", s.dna.observations)
                    .put("baselines", baselines).put("biases", biases).put("verifiedCount", s.dna.verifiedCount).put("totalAbsoluteErrorC", s.dna.totalAbsoluteErrorC))
                .put("events", JSONArray(s.events.map { JSONObject().put("id", it.id).put("wallMs", it.wallMs).put("kind", it.kind).put("message", it.message) }))
                .put("verifications", JSONArray(s.verifications.map { v -> JSONObject().put("id", v.id).put("wallMs", v.wallMs)
                    .put("horizonMinutes", v.horizonMinutes).putNullable("predictedC", v.predictedC).putNullable("actualC", v.actualC)
                    .putNullable("signedErrorC", v.signedErrorC).putNullable("predictedBattery", v.predictedBattery)
                    .putNullable("actualBattery", v.actualBattery).put("action", v.action).put("status", v.status)
                    .put("contextChanged", v.contextChanged).putNullable("baselineTemperature", v.baselineTemperature).put("biasApplied", v.biasApplied) }))
            if (blackBoxEvents.isNotEmpty()) {
                root.put("blackBox", JSONArray(blackBoxEvents.takeLast(100).map { bb ->
                    JSONObject().put("id", bb.id)
                        .put("timestamp", bb.timestamp)
                        .put("eventType", bb.eventType)
                        .putNullable("intervention", bb.intervention)
                        .putNullable("predictedOutcome", bb.predictedOutcome)
                        .putNullable("actualOutcome", bb.actualOutcome)
                        .putNullable("predictionError", bb.predictionError)
                        .put("notes", bb.notes)
                }))
            }
            if (hardware != null) root.put("hardware", JSONObject().put("manufacturer", hardware.manufacturer)
                .put("model", hardware.model).put("android", hardware.androidVersion).put("sdk", hardware.sdk)
                .putNullable("soc", hardware.soc).put("path", ArchitectureSelector.select(hardware).name)
                .put("secondaryChip", hardware.secondaryChip.name).put("evidence", hardware.evidence)
                .put("gyroAvailable", hardware.gyroAvailable)
                .put("accelerometerAvailable", hardware.accelerometerAvailable)
                .put("linearAccelerationAvailable", hardware.linearAccelerationAvailable)
                .put("rotationVectorAvailable", hardware.rotationVectorAvailable)
                .put("hapticHardwareAvailable", hardware.hapticHardwareAvailable)
                .put("hapticObservationSupported", hardware.hapticObservationSupported))
            // Export contains the bounded current session window; checkpoints only need event summaries.
            if (hardware != null) root.put("samples", JSONArray(s.history.map { p -> JSONObject().put("elapsedMs", p.elapsedMs)
                .put("wallMs", p.wallMs).putNullable("batteryPct", p.batteryPct).putNullable("batteryC", p.batteryC)
                .putNullable("charging", p.charging).putNullable("thermalStatus", p.thermalStatus).putNullable("headroom", p.headroom)
                .putNullable("availableMemoryBytes", p.availableMemoryBytes).putNullable("totalMemoryBytes", p.totalMemoryBytes)
                .putNullable("appPssKb", p.appPssKb).put("processCpuMs", p.processCpuMs).put("collectionMs", p.collectionMs).put("workload", p.workload.name) }))
            return root
        }
        private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
        private fun JSONObject.number(name: String): Double? = if (isNull(name)) null else optDouble(name).takeIf { it.isFinite() }
    }
}

