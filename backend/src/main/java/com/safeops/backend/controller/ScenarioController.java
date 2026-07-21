package com.safeops.backend.controller;

import com.safeops.backend.dto.request.SimulatorModeRequest;
import com.safeops.backend.dto.response.SimulatorStatusResponse;
import com.safeops.backend.simulator.SensorSimulator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/scenario")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Scenarios", description = "Preset demo scenario triggers for hackathon presentations")
public class ScenarioController {

    private final SensorSimulator sensorSimulator;

    @PostMapping("/trigger")
    @Operation(summary = "Trigger preset historical accident scenarios (VIZAG_2025, HPCL_2013, BHOPAL_OVERPRESSURE, NORMAL)")
    public ResponseEntity<SimulatorStatusResponse> triggerScenario(@RequestBody Map<String, String> payload) {
        String scenario = payload.getOrDefault("scenario", "VIZAG_2025").toUpperCase();
        log.info(">>> Triggering Preset Historical Scenario: {} <<<", scenario);

        SimulatorModeRequest modeRequest = new SimulatorModeRequest();

        switch (scenario) {
            case "VIZAG_2025" -> {
                modeRequest.setZoneId("ZONE_3");
                modeRequest.setSimulationMode("SPIKE");
                modeRequest.setShiftRiskFactor(0.2);
            }
            case "HPCL_2013" -> {
                modeRequest.setZoneId("ZONE_2");
                modeRequest.setSimulationMode("DRIFT");
                modeRequest.setShiftRiskFactor(0.15);
            }
            case "BHOPAL_OVERPRESSURE" -> {
                modeRequest.setZoneId("ZONE_1");
                modeRequest.setSimulationMode("SPIKE");
                modeRequest.setShiftRiskFactor(0.3);
            }
            default -> {
                modeRequest.setZoneId("ZONE_3");
                modeRequest.setSimulationMode("NORMAL");
                modeRequest.setShiftRiskFactor(0.1);
            }
        }

        SimulatorStatusResponse status = sensorSimulator.updateConfiguration(modeRequest);
        return ResponseEntity.ok(status);
    }
}
