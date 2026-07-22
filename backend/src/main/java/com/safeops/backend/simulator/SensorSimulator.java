package com.safeops.backend.simulator;

import com.safeops.backend.dto.request.CvFrameRequest;
import com.safeops.backend.dto.request.SimulatorModeRequest;
import com.safeops.backend.dto.response.SimulatorStatusResponse;
import com.safeops.backend.entity.SafetyEvaluationLog;
import com.safeops.backend.repository.SafetyEvaluationLogRepository;
import com.safeops.backend.repository.UserRepository;
import com.safeops.backend.service.SafetyService;
import com.safeops.backend.service.SmsService;
import com.safeops.backend.entity.Role;
import com.safeops.backend.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SensorSimulator implements SensorSimulatorControl {

    private static final int HISTORY_WINDOW = 6;
    private static final double DEFAULT_GAS = 18.0;
    private static final double DEFAULT_TEMP = 28.0;
    private static final double DEFAULT_PRESSURE = 1.2;

    private final SafetyService safetyService;
    private final SafetyEvaluationLogRepository safetyEvaluationLogRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final SmsService smsService;
    private final Random random = new Random();
    private final Deque<List<Double>> sensorHistory = new ArrayDeque<>();

    // 1. Thread-safe Live CV Buffer (ZoneId -> Live Frame + Timestamp)
    private final Map<String, LiveCvHolder> liveCvBuffer = new ConcurrentHashMap<>();

    private static class LiveCvHolder {
        final CvFrameRequest request;
        final Instant timestamp;

        LiveCvHolder(CvFrameRequest request) {
            this.request = request;
            this.timestamp = Instant.now();
        }

        boolean isFresh() {
            return Duration.between(timestamp, Instant.now()).getSeconds() <= 15;
        }
    }

    private String simulationMode = "NORMAL";
    private String zoneId = "ZONE_3";
    private double shiftRiskFactor = 0.1;
    private double currentGas = DEFAULT_GAS;
    private double currentTemp = DEFAULT_TEMP;
    private double currentPressure = DEFAULT_PRESSURE;
    private Instant lastReadingAt;

    public SensorSimulator(SafetyService safetyService,
                           SafetyEvaluationLogRepository safetyEvaluationLogRepository,
                           SimpMessagingTemplate messagingTemplate,
                           UserRepository userRepository,
                           SmsService smsService) {
        this.safetyService = safetyService;
        this.safetyEvaluationLogRepository = safetyEvaluationLogRepository;
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
        this.smsService = smsService;
    }

    // 2. Public method to register live CCTV detection frame from CvController
    public void updateLiveCvFrame(CvFrameRequest frameRequest) {
        if (frameRequest != null && frameRequest.getZoneId() != null) {
            liveCvBuffer.put(frameRequest.getZoneId(), new LiveCvHolder(frameRequest));
            log.info("Updated live CV frame buffer for {}", frameRequest.getZoneId());
        }
    }

    public synchronized SimulatorStatusResponse updateConfiguration(SimulatorModeRequest request) {
        String requestedMode = normalizeMode(request.getSimulationMode());
        if (request.getZoneId() != null && !request.getZoneId().isBlank() && !request.getZoneId().equals(zoneId)) {
            zoneId = request.getZoneId().trim();
            resetSimulationBaseline();
        }

        simulationMode = requestedMode;
        return getStatus();
    }

    public synchronized SimulatorStatusResponse getStatus() {
        return SimulatorStatusResponse.builder()
                .simulationMode(simulationMode)
                .zoneId(zoneId)
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
                safetyService.cacheLatestEvaluation(zoneId, result);
                messagingTemplate.convertAndSend("/topic/safety/" + zoneId, result);

                // Send Twilio alert if simulationMode is SPIKE or DRIFT and action is DISPATCH_ALERT
                if ("SPIKE".equalsIgnoreCase(simulationMode) || "DRIFT".equalsIgnoreCase(simulationMode)) {
                    Object actionObj = result.get("action_taken");
                    String actionTakenStr = actionObj != null ? actionObj.toString() : "";

                    if ("DISPATCH_ALERT".equalsIgnoreCase(actionTakenStr) || "LOG_WARNING".equalsIgnoreCase(actionTakenStr)) {
                        log.info("Simulator condition met ({} in {} mode). Fetching ADMIN users to send SMS alert.", actionTakenStr, simulationMode);
                        List<User> admins = userRepository.findByRole(Role.ADMIN);

                        // Extract risk information
                        String severity = "NORMAL";
                        double riskScorePct = 0.0;
                        Object riskFusionObj = result.get("risk_fusion_out");
                        if (riskFusionObj instanceof Map<?, ?> riskFusionMap) {
                            Object sevVal = riskFusionMap.get("severity");
                            if (sevVal != null) {
                                severity = sevVal.toString();
                            }
                            Object scoreVal = riskFusionMap.get("score");
                            if (scoreVal instanceof Number num) {
                                riskScorePct = num.doubleValue() * 100.0;
                            }
                        }

                        // Extract Gas Spike info
                        double gasVal = 0.0;
                        double gasBaseline = 0.0;
                        String gasTrend = "STABLE";
                        int breachMin = 0;
                        Object anomalyObj = result.get("sensor_anomaly_out");
                        if (anomalyObj instanceof Map<?, ?> anomalyMap) {
                            Object val = anomalyMap.get("current_value");
                            if (val instanceof Number num) gasVal = num.doubleValue();
                            Object base = anomalyMap.get("normal_baseline");
                            if (base instanceof Number num) gasBaseline = num.doubleValue();
                            Object tr = anomalyMap.get("trend");
                            if (tr != null) gasTrend = tr.toString();
                            Object br = anomalyMap.get("predicted_threshold_breach_minutes");
                            if (br instanceof Number num) breachMin = num.intValue();
                        }

                        // Extract Conflict info
                        String conflictDesc = "None";
                        Object permitIntelObj = result.get("permit_intel_out");
                        if (permitIntelObj instanceof Map<?, ?> permitIntelMap) {
                            Object conflictsVal = permitIntelMap.get("conflicts");
                            if (conflictsVal instanceof Collection<?> conflictsColl && !conflictsColl.isEmpty()) {
                                Object firstConflict = conflictsColl.iterator().next();
                                if (firstConflict instanceof Map<?, ?> conflictMap) {
                                    Object descVal = conflictMap.get("risk_description");
                                    if (descVal != null) conflictDesc = descVal.toString();
                                }
                            }
                        }

                        // Extract PPE Violations
                        int workers = 0;
                        StringBuilder ppeBuilder = new StringBuilder();
                        Object cvSafetyObj = result.get("cv_safety_out");
                        if (cvSafetyObj instanceof Map<?, ?> cvSafetyMap) {
                            Object wVal = cvSafetyMap.get("workers_detected");
                            if (wVal instanceof Number num) workers = num.intValue();
                            Object violationsVal = cvSafetyMap.get("violations");
                            if (violationsVal instanceof Collection<?> violationsColl) {
                                for (Object viol : violationsColl) {
                                    if (viol instanceof Map<?, ?> violMap) {
                                        Object wId = violMap.get("worker_id");
                                        Object vType = violMap.get("violation_type");
                                        if (wId != null && vType != null) {
                                            if (ppeBuilder.length() > 0) ppeBuilder.append(", ");
                                            ppeBuilder.append(String.format("%s: %s", wId, vType));
                                        }
                                    }
                                }
                            }
                        }
                        String ppeViolations = ppeBuilder.length() > 0 ? ppeBuilder.toString() : "None";

                        // Extract Recommended Actions (first 2 actions to stay concise)
                        StringBuilder actionsBuilder = new StringBuilder();
                        String incidentDesc = "None";
                        Object ragComplianceObj = result.get("rag_compliance_out");
                        if (ragComplianceObj instanceof Map<?, ?> ragComplianceMap) {
                            // Extract description from similar_incidents
                            Object incidentsVal = ragComplianceMap.get("similar_incidents");
                            if (incidentsVal instanceof Collection<?> incidentsColl && !incidentsColl.isEmpty()) {
                                Object firstIncident = incidentsColl.iterator().next();
                                if (firstIncident instanceof Map<?, ?> incidentMap) {
                                    Object descVal = incidentMap.get("description");
                                    if (descVal != null) {
                                        incidentDesc = descVal.toString();
                                    }
                                }
                            }

                            // Extract recommended actions
                            Object recActionsObj = ragComplianceMap.get("recommended_actions");
                            if (recActionsObj instanceof Collection<?> recActionsColl) {
                                int count = 0;
                                for (Object act : recActionsColl) {
                                    if (act != null && count < 2) {
                                        actionsBuilder.append("\n- ").append(act.toString());
                                        count++;
                                    }
                                }
                            }
                        }

                        // Construct the formatted message (fully dynamic using response messages)
                        String alertMsg = String.format(
                                "🚨SafeOps Alert: %s (%s Mode)\n" +
                                "Risk: %s (%.1f%%)\n" +
                                "Incident: %s%s",
                                zoneId, simulationMode.toUpperCase(),
                                severity, riskScorePct,
                                incidentDesc,
                                actionsBuilder.toString()
                        );

                        if (admins != null && !admins.isEmpty()) {
                            for (User admin : admins) {
                                if (admin.getPhoneNumber() != null && !admin.getPhoneNumber().isBlank()) {
                                    smsService.sendSms(admin.getPhoneNumber(), alertMsg);
                                }
                            }
                        } else {
                            log.warn("No ADMIN users with phone numbers found to send alert in {} mode.", simulationMode);
                        }
                    }
                }

                Double riskScore = null;
                String severity = "NORMAL";
                String actionTaken = result.get("action_taken") != null ? result.get("action_taken").toString() : null;

                Object riskFusionObj = result.get("risk_fusion_out");
                if (riskFusionObj instanceof Map<?, ?> riskFusionMap) {
                    if (riskFusionMap.get("score") instanceof Number num) {
                        riskScore = num.doubleValue();
                    }
                    if (riskFusionMap.get("severity") != null) {
                        severity = riskFusionMap.get("severity").toString();
                    }
                }

                if ("DISPATCH_ALERT".equalsIgnoreCase(actionTaken) || "TRIGGER_EVACUATION".equalsIgnoreCase(actionTaken) || "HIGH".equalsIgnoreCase(severity) || "CRITICAL".equalsIgnoreCase(severity)) {
                    SafetyEvaluationLog logEntry = SafetyEvaluationLog.builder()
                            .zoneId(zoneId)
                            .riskScore(riskScore)
                            .severity(severity)
                            .actionTaken(actionTaken)
                            .rawResult(result)
                            .build();
                    safetyEvaluationLogRepository.save(logEntry);
                    log.info("Persisted safety evaluation log for {}", zoneId);
                }
            }
        } catch (Exception e) {
            log.warn("Safety evaluation failed for {}: {}", zoneId, e.getMessage());
        }
    }

    // 3. DISABLE AUTOMATIC MODE ROTATION (Mode stays locked to user/API selection)
    // @Scheduled(fixedDelay = 10000)
    public synchronized void rotateSimulationMode() {
        log.debug("Auto rotation bypassed. Active mode: {}", simulationMode);
    }

    private List<Double> nextReading() {
        String mode = simulationMode == null ? "NORMAL" : simulationMode.toUpperCase(Locale.ROOT);

        switch (mode) {
            case "DRIFT" -> {
                currentGas += 0.4;
                currentTemp += 0.5;
                currentPressure += 0.5;
            }
            case "SPIKE" -> {
                currentGas = 38.0 + (random.nextDouble() * 1.5 - 0.75);
                currentTemp = 32.0 + (random.nextDouble() * 0.4);
                currentPressure = 1.25 + (random.nextDouble() * 0.02);
            }
            default -> {
                currentGas += (random.nextGaussian() * 0.05) + (DEFAULT_GAS - currentGas) * 0.05;
                currentTemp += (random.nextGaussian() * 0.03) + (DEFAULT_TEMP - currentTemp) * 0.05;
                currentPressure += (random.nextGaussian() * 0.005) + (DEFAULT_PRESSURE - currentPressure) * 0.05;
            }
        }

        currentGas = clamp(currentGas, 0.0, 100.0);
        currentTemp = clamp(currentTemp, 0.0, 120.0);
        currentPressure = clamp(currentPressure, 0.0, 10.0);

        return List.of(round(currentGas), round(currentTemp), round(currentPressure));
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

    // 4. Check live CV buffer first; fallback to simulation mock frame
    private Map<String, Object> buildCvRawFrame() {
        LiveCvHolder liveHolder = liveCvBuffer.get(zoneId);

        if (liveHolder != null && liveHolder.isFresh()) {
            CvFrameRequest live = liveHolder.request;
            return Map.of(
                    "zone_id", live.getZoneId(),
                    "workers_detected", live.getWorkersDetected(),
                    "violations", live.getViolations() != null ? live.getViolations() : List.of()
            );
        }

        boolean spikeMode = "SPIKE".equalsIgnoreCase(simulationMode);
        boolean driftMode = "DRIFT".equalsIgnoreCase(simulationMode);

        List<Map<String, Object>> mockViolations = List.of();
        if (spikeMode) {
            mockViolations = List.of(
                    Map.of("worker_id", "W_101", "violation_type", "NO_HELMET", "confidence", 0.91),
                    Map.of("worker_id", "W_102", "violation_type", "NO_VEST", "confidence", 0.85)
            );
        } else if (driftMode && currentGas > 24.0) {
            mockViolations = List.of(
                    Map.of("worker_id", "W_101", "violation_type", "NO_HELMET", "confidence", 0.78)
            );
        }

        return Map.of(
                "zone_id", zoneId,
                "workers_detected", spikeMode ? 3 : (driftMode ? 2 : 1),
                "violations", mockViolations
        );
    }

    // 5. Dynamic Permit Ingestion based on Active Scenario
    private List<Map<String, Object>> buildActivePermitsRaw() {
        if (!"SPIKE".equalsIgnoreCase(simulationMode) && !"DRIFT".equalsIgnoreCase(simulationMode)) {
            return List.of();
        }

        String permitType = "SPIKE".equalsIgnoreCase(simulationMode) ? "HOT_WORK" : "CONFINED_SPACE";

        return List.of(Map.of(
                "permit_id", "HW_042",
                "type", permitType,
                "zone_id", zoneId,
                "start_time", Instant.now().minusSeconds(300).toString(),
                "expiry", Instant.now().plusSeconds(8 * 3600).toString()
        ));
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
            throw new IllegalArgumentException("Unsupported simulation mode: " + mode);
        }
        return normalized;
    }

    private String buildSensorId(String sensorType) {
        String normalizedType = sensorType == null ? "SENSOR" : sensorType.trim().toUpperCase(Locale.ROOT);
        String normalizedZone = zoneId == null ? "ZONE_3" : zoneId.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return normalizedType + "_" + normalizedZone;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
