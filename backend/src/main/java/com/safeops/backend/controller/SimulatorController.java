package com.safeops.backend.controller;

import com.safeops.backend.dto.request.SimulatorModeRequest;
import com.safeops.backend.dto.response.SimulatorStatusResponse;
import com.safeops.backend.simulator.SensorSimulatorControl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulator")
@RequiredArgsConstructor
@Tag(name = "Simulator", description = "Dynamic sensor simulator control endpoints")
public class SimulatorController {

    private final SensorSimulatorControl sensorSimulator;

    @GetMapping("/mode")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Get the current simulator mode and runtime snapshot")
    public ResponseEntity<SimulatorStatusResponse> getMode() {
        return ResponseEntity.ok(sensorSimulator.getStatus());
    }

    @PutMapping("/mode")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(summary = "Update the simulator mode, zone, and risk factor")
    public ResponseEntity<SimulatorStatusResponse> updateMode(@Valid @RequestBody SimulatorModeRequest request) {
        return ResponseEntity.ok(sensorSimulator.updateConfiguration(request));
    }
}


