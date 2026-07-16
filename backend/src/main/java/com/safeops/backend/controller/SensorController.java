package com.safeops.backend.controller;

import com.safeops.backend.dto.request.SensorRequest;
import com.safeops.backend.service.ModelClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sensor")
@RequiredArgsConstructor
@Tag(name = "Sensor", description = "Sensor anomaly detection endpoints")
public class SensorController {

    private final ModelClientService modelClientService;

    @PostMapping("/anomaly")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Run sensor anomaly detection (synchronous)")
    public ResponseEntity<Map<String, Object>> checkAnomaly(@Valid @RequestBody SensorRequest request) {
        Map<String, Object> result = modelClientService.checkSensorAnomaly(
                Map.of("readings", request.getReadings())
        );
        return ResponseEntity.ok(result);
    }
}
