package com.safeops.backend.simulator;

import com.safeops.backend.dto.request.SimulatorModeRequest;
import com.safeops.backend.dto.response.SimulatorStatusResponse;
import com.safeops.backend.service.SafetyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.safeops.backend.entity.SafetyEvaluationLog;
import com.safeops.backend.repository.SafetyEvaluationLogRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

@Component
@Slf4j
public class SensorSimulator implements SensorSimulatorControl {

    private static final int HISTORY_WINDOW = 12;
    private static final double DEFAULT_GAS = 18.0;
    private static final double DEFAULT_TEMP = 28.0;
    private static final double DEFAULT_PRESSURE = 1.2;

    private final SafetyService safetyService;
    private final SafetyEvaluationLogRepository safetyEvaluationLogRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Random random = new Random();
    private final Deque<List<Double>> sensorHistory = new ArrayDeque<>();

    private String simulationMode = "NORMAL"; // NORMAL, DRIFT, SPIKE
    private String zoneId = "ZONE_3";
    private double shiftRiskFactor = 0.1;
    private double currentGas = DEFAULT_GAS;
    private double currentTemp = DEFAULT_TEMP;
    private double currentPressure = DEFAULT_PRESSURE;
    private Instant lastReadingAt;

    public SensorSimulator(SafetyService safetyService,
                           SafetyEvaluationLogRepository safetyEvaluationLogRepository,
                           SimpMessagingTemplate messagingTemplate) {
        this.safetyService = safetyService;
        this.safetyEvaluationLogRepository = safetyEvaluationLogRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public synchronized SimulatorStatusResponse updateConfiguration(SimulatorModeRequest request) {
        String requestedMode = normalizeMode(request.getSimulationMode());
        if (request.getZoneId() != null && !request.getZoneId().isBlank() && !request.getZoneId().equals(zoneId)) {
            zoneId = request.getZoneId().trim();
            resetSimulationBaseline();
        }

        simulationMode = requestedMode;
        if (request.getShiftRiskFactor() != null) {
            shiftRiskFactor = request.getShiftRiskFactor();
        }

        return getStatus();
    }

    public synchronized SimulatorStatusResponse getStatus() {
        return SimulatorStatusResponse.builder()
                .simulationMode(simulationMode)
                .zoneId(zoneId)
                .shiftRiskFactor(shiftRiskFactor)
                .bufferedReadings(sensorHistory.size())
                .sensorId(buildSensorId("GAS"))
                .latestReading(sensorHistory.peekLast() == null ? null : new ArrayList<>(sensorHistory.peekLast()))
                .latestReadingAt(lastReadingAt)
                .build();
    }

    @Scheduled(fixedDelayString = "${safeops.simulator.fixed-delay-ms:5000}")
    public synchronized void generateReadings() {
        List<Double> reading = nextReading();
        sensorHistory.addLast(reading);
        while (sensorHistory.size() > HISTORY_WINDOW) {
            sensorHistory.removeFirst();
        }

        lastReadingAt = Instant.now();
        log.info("Generated sensor reading for {} in mode {}: {}", zoneId, simulationMode, reading);

        if (sensorHistory.size() < HISTORY_WINDOW) {
            log.info("Warm-up phase for {}: collected {}/{} readings", zoneId, sensorHistory.size(), HISTORY_WINDOW);
            return;
        }

        Map<String, Object> payload = buildSafetyPayload(new ArrayList<>(sensorHistory));
        try {
            Map<String, Object> result = safetyService.evaluateSafety(payload)
                    .timeout(Duration.ofSeconds(20))
                    .block();
            
            log.info("Safety evaluation result for {}: {}", zoneId, result);

            if (result != null) {
                // 1. Store in the in-memory cache
                safetyService.cacheLatestEvaluation(zoneId, result);

                // 2. Broadcast via WebSocket to subscribers
                messagingTemplate.convertAndSend("/topic/safety/" + zoneId, result);
                log.debug("Broadcasted safety evaluation for {} via WebSocket", zoneId);

                // 3. Extract metrics and conditionally persist high-risk evaluations
                Double riskScore = null;
                String severity = "NORMAL";
                String actionTaken = null;

                if (result.get("action_taken") != null) {
                    actionTaken = result.get("action_taken").toString();
                }

                Object riskFusionObj = result.get("risk_fusion_out");
                if (riskFusionObj instanceof Map<?, ?> riskFusionMap) {
                    if (riskFusionMap.get("score") instanceof Number num) {
                        riskScore = num.doubleValue();
                    }
                    if (riskFusionMap.get("severity") != null) {
                        severity = riskFusionMap.get("severity").toString();
                    }
                }

                if ("DISPATCH_ALERT".equalsIgnoreCase(actionTaken) || "HIGH".equalsIgnoreCase(severity) || "CRITICAL".equalsIgnoreCase(severity)) {
                    SafetyEvaluationLog logEntry = SafetyEvaluationLog.builder()
                            .zoneId(zoneId)
                            .riskScore(riskScore)
                            .severity(severity)
                            .actionTaken(actionTaken)
                            .rawResult(result)
                            .build();
                    safetyEvaluationLogRepository.save(logEntry);
                    log.info("Persisted high-risk safety evaluation log to database for {}", zoneId);
                }
            }
        } catch (Exception e) {
            log.warn("Safety evaluation failed for {}: {}", zoneId, e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 10000)
    public synchronized void rotateSimulationMode() {
        switch (simulationMode) {
            case "NORMAL" -> {
                simulationMode = "DRIFT";
                log.info(">>> Rotating simulation mode from NORMAL to DRIFT <<<");
            }
            case "DRIFT" -> {
                simulationMode = "SPIKE";
                log.info(">>> Rotating simulation mode from DRIFT to SPIKE <<<");
            }
            case "SPIKE" -> {
                simulationMode = "NORMAL";
                log.info(">>> Rotating simulation mode from SPIKE to NORMAL (resettling baseline) <<<");
                currentGas = DEFAULT_GAS;
                currentTemp = DEFAULT_TEMP;
                currentPressure = DEFAULT_PRESSURE;
            }
            default -> {
                simulationMode = "NORMAL";
            }
        }
    }

    private List<Double> nextReading() {
        String mode = simulationMode == null ? "NORMAL" : simulationMode.toUpperCase(Locale.ROOT);

        switch (mode) {
            case "DRIFT" -> {
                currentGas += 0.5;
                currentTemp += 0.08;
                currentPressure += 0.01;
            }
            case "SPIKE" -> {
                currentGas = 38.0;
                currentTemp = 31.0;
                currentPressure = 1.24;
            }
            default -> {
                currentGas += (random.nextGaussian() * 0.05) + (DEFAULT_GAS - currentGas) * 0.01;
                currentTemp += (random.nextGaussian() * 0.03) + (DEFAULT_TEMP - currentTemp) * 0.01;
                currentPressure += (random.nextGaussian() * 0.005) + (DEFAULT_PRESSURE - currentPressure) * 0.01;
            }
        }

        currentGas = clamp(currentGas, 0.0, 100.0);
        currentTemp = clamp(currentTemp, 0.0, 120.0);
        currentPressure = clamp(currentPressure, 0.0, 10.0);

        return List.of(round(currentGas), round(currentTemp), round(currentPressure));
    }

    private void resetSimulationBaseline() {
        sensorHistory.clear();
        currentGas = DEFAULT_GAS;
        currentTemp = DEFAULT_TEMP;
        currentPressure = DEFAULT_PRESSURE;
        lastReadingAt = null;
    }

    private String normalizeMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        if (!"NORMAL".equals(normalized) && !"DRIFT".equals(normalized) && !"SPIKE".equals(normalized)) {
            throw new IllegalArgumentException("Unsupported simulation mode: " + mode + ". Allowed values: NORMAL, DRIFT, SPIKE");
        }
        return normalized;
    }

    private String buildSensorId(String sensorType) {
        String normalizedType = sensorType == null ? "SENSOR" : sensorType.trim().toUpperCase(Locale.ROOT);
        String normalizedZone = zoneId == null ? "ZONE_3" : zoneId.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return normalizedType + "_" + normalizedZone;
    }


    private Map<String, Object> buildSafetyPayload(List<List<Double>> history) {
        return Map.of(
                "zone_id", zoneId,
                "current_time", Instant.now().toString(),
                "shift_risk_factor", shiftRiskFactor,
                "sensor_raw_history", history,
                "cv_raw_frame", buildCvRawFrame(),
                "active_permits_raw", buildActivePermitsRaw()
        );
    }

    private Map<String, Object> buildCvRawFrame() {
        boolean spikeMode = "SPIKE".equalsIgnoreCase(simulationMode);
        return Map.of(
                "zone_id", zoneId,
                "workers_detected", spikeMode ? 3 : 2,
                "violations", spikeMode
                        ? List.of(Map.of(
                        "worker_id", "W_101",
                        "violation_type", "NO_HELMET",
                        "confidence", 0.9
                ))
                        : List.of()
        );
    }

    private List<Map<String, Object>> buildActivePermitsRaw() {
        if (!"SPIKE".equalsIgnoreCase(simulationMode)) {
            return List.of();
        }

        return List.of(Map.of(
                "permit_id", "HW_042",
                "type", "HOT_WORK",
                "zone_id", zoneId,
                "start_time", Instant.now().minusSeconds(10).toString(),
                "expiry", Instant.now().plusSeconds(8 * 60 * 60).toString()
        ));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

