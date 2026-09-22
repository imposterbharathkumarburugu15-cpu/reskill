package dev.sovarix.core

import org.junit.Assert.*
import org.junit.Test

class TwinEngineTest {
    private val cfg = GovernorConfig()
    private fun sample(t: Long, c: Double? = 30.0 + t / 600_000.0, charging: Boolean? = false,
        thermal: Int? = 0, workload: Workload = Workload.IDLE, interactive: Boolean = true,
        battery: Double? = 80.0) = Sample(t, 1_700_000_000_000L + t, battery, c, charging, null,
        thermal, null, 4_000_000_000, 8_000_000_000, false, 20_000, t / 1_000, false, interactive, workload, 1.0)
    private fun history(duration: Int = 180) = (0..duration step 10).map { sample(it * 1_000L) }

    @Test fun unknownChipUsesStandardAndVerifiedAdapterUsesAdvanced() {
        val p = CapabilityProfile("vivo", "real model", "15", 35, null, "arm64-v8a", 8)
        assertEquals(TwinPath.STANDARD, ArchitectureSelector.select(p))
        assertEquals(TwinPath.STANDARD, ArchitectureSelector.select(p.copy(secondaryChip = Availability.AVAILABLE)))
        assertEquals(TwinPath.ADVANCED, ArchitectureSelector.select(p.copy(secondaryChip = Availability.AVAILABLE, vendorTelemetryVerified = true)))
    }
    @Test fun unavailableAndShortHistoryDoNotProducePredictions() {
        val f = ForecastEngine(cfg)
        assertNull(f.forecast(history(60), 2, DeviceDNA()).temperature)
        assertNull(f.forecast(history().map { it.copy(batteryC = null) }, 2, DeviceDNA()).temperature)
        assertNull(f.forecast(history(), 5, DeviceDNA()).temperature)
    }
    @Test fun correctTrendUsesElapsedTime() {
        val p = ForecastEngine(cfg).forecast(history(), 2, DeviceDNA()).temperature!!
        assertEquals(30.5, p.value, 0.00001); assertTrue(p.heuristicBand > 0); assertEquals(19, p.samples)
    }
    @Test fun chargingAndWorkloadChangesResetTheWindow() {
        assertNull(ForecastEngine(cfg).forecast(history() + sample(190_000, charging = true), 2, DeviceDNA()).temperature)
        assertNull(ForecastEngine(cfg).forecast(history() + sample(190_000, workload = Workload.GAMING), 2, DeviceDNA()).temperature)
    }
    @Test fun gapAndMissingLatestSensorInvalidateForecast() {
        assertNull(ForecastEngine(cfg).forecast(history() + sample(600_000), 2, DeviceDNA()).temperature)
        assertNull(ForecastEngine(cfg).forecast(history() + sample(190_000, c = null), 2, DeviceDNA()).temperature)
    }
    @Test fun constantBatteryHasNonzeroSpread() {
        val p = ForecastEngine(cfg).forecast(history(), 2, DeviceDNA()).battery!!
        assertEquals(80.0, p.value, 0.0); assertTrue(p.heuristicBand >= 1.0)
    }
    @Test fun burstIsBoundedAndRecoveryPreventsImmediateRetrigger() {
        val c = ObservationController(cfg)
        assertEquals(ObservationMode.HIGH_ACTIVITY, c.choose(sample(0), 0.6, null, false).mode)
        assertEquals(ObservationMode.HIGH_ACTIVITY, c.choose(sample(20_000), 0.6, null, false).mode)
        assertEquals(ObservationMode.RECOVERY, c.choose(sample(30_000), 0.6, null, false).mode)
        assertEquals(ObservationMode.RECOVERY, c.choose(sample(80_000), 0.6, null, false).mode)
        assertEquals(ObservationMode.HIGH_ACTIVITY, c.choose(sample(90_000), 0.6, null, false).mode)
    }
    @Test fun thermalPressureAndBudgetOverrideFasterObservation() {
        val c = ObservationController(cfg)
        assertEquals(ObservationMode.LOW_POWER, c.choose(sample(0, thermal = 3), 3.0, null, false).mode)
        assertTrue(c.choose(sample(10_000, thermal = 4), 3.0, null, false).stopSession)
        assertEquals(ObservationMode.LOW_POWER, c.choose(sample(20_000, interactive = false), 3.0, null, false).mode)
        assertEquals(ObservationMode.LOW_POWER, c.choose(sample(30_000, battery = 5.0), 3.0, null, false).mode)
        assertEquals(ObservationMode.LOW_POWER, c.choose(sample(40_000), null, Overhead(20.0, 50.0, 1.0, 4, 1.0, 10000), false).mode)
    }
    @Test fun simulationDoesNotMutateMeasurements() {
        val h = history(); val copy = h.toList(); val f = ForecastEngine(cfg).forecast(h, 2, DeviceDNA())
        val simulated = SimulationEngine.simulate(h, f, 0.0)
        assertEquals(h.last().batteryC, simulated.temperatureC); assertEquals(copy, h); assertTrue(simulated.explanation.contains("NOT REAL"))
    }
    @Test fun lateActualIsNotUsedAsFalseProof() {
        val f = ForecastEngine(cfg).forecast(history(), 2, DeviceDNA()); val ticket = PredictionTicket(1, f, Workload.IDLE, false, "test", 30.3)
        val v = VerificationEngine(cfg); assertNull(v.resolve(ticket, sample(290_000), false))
        val missed = v.resolve(ticket, sample(400_000), false)!!; assertNull(missed.actualC); assertTrue(missed.status.startsWith("MISSED"))
    }
    @Test fun completeLoopCalibratesRealErrorAndEndsCleanly() {
        val e = TwinEngine(); e.setWorkload(Workload.IDLE, 0); e.start(0, 0); history().forEach { e.accept(it) }
        assertTrue(e.pin(2, 180_000)); for (t in 190..300 step 10) e.accept(sample(t * 1_000L))
        assertEquals(1, e.state.verifications.size); assertEquals(1, e.state.dna.verifiedCount); assertTrue(e.state.pending.isEmpty())
        e.stop(301_000); e.stop(302_000); assertEquals(1, e.state.dna.sessions); assertFalse(e.state.running)
    }
    @Test fun interventionIsRecordedButNotUsedForCalibration() {
        val e = TwinEngine(); e.setWorkload(Workload.IDLE, 0); e.start(0, 0); history().forEach { e.accept(it) }
        assertTrue(e.pin(2, 180_000)); e.markIntervention(190_000, "Reduced workload manually")
        for (t in 190..300 step 10) e.accept(sample(t * 1_000L))
        assertEquals(0, e.state.dna.verifiedCount); assertTrue(e.state.verifications.single().contextChanged)
    }
    @Test fun contextChangeAwayAndBackInvalidatesCalibration() {
        val e = TwinEngine(); e.setWorkload(Workload.IDLE, 0); e.start(0, 0); history().forEach { e.accept(it) }; e.pin(2, 180_000)
        e.setWorkload(Workload.GAMING, 190_000); e.setWorkload(Workload.IDLE, 191_000)
        assertFalse(e.pin(2, 191_000)); for (t in 190..300 step 10) e.accept(sample(t * 1_000L))
        assertEquals(0, e.state.dna.verifiedCount)
    }
    @Test fun stopCancelsPendingAndBoundsHistory() {
        val e = TwinEngine(config = cfg.copy(historyCapacity = 30)); e.start(0, 0)
        for (t in 0..600 step 10) e.accept(sample(t * 1_000L))
        assertTrue(e.state.history.size <= 30); e.pin(2, 600_000); e.stop(610_000)
        assertTrue(e.state.pending.isEmpty()); assertTrue(e.state.verifications.last().status.startsWith("CANCELLED"))
    }
    @Test fun actionPermissionsAreExplicit() {
        assertEquals(ActionPermission.NOT_ALLOWED, ActionPolicy.permission(TwinAction.Q_CHIP_CONTROL))
        assertEquals(ActionPermission.NOT_ALLOWED, ActionPolicy.permission(TwinAction.CPU_CONTROL))
        assertEquals(ActionPermission.USER_CONFIRMATION_REQUIRED, ActionPolicy.permission(TwinAction.DIM_THIS_APP))
    }
}
