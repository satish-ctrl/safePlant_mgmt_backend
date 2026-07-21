package com.safeops.backend.controller;

import com.safeops.backend.dto.request.CvFrameRequest;
import com.safeops.backend.simulator.SensorSimulator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cv")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Video Analytics", description = "Endpoints for ingesting live CCTV frame detections from YOLO edge nodes")
public class CvController {

    private final SensorSimulator sensorSimulator;

    @PostMapping("/frame")
    @Operation(summary = "Ingest live video analytics detection frame from edge camera node")
    public ResponseEntity<Map<String, Object>> ingestCvFrame(@Valid @RequestBody CvFrameRequest request) {
        log.info("Received live CV detection frame for zone {}: {} workers, {} violations",
                request.getZoneId(), request.getWorkersDetected(),
                request.getViolations() != null ? request.getViolations().size() : 0);

        sensorSimulator.updateLiveCvFrame(request);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "zoneId", request.getZoneId(),
                "message", "Live CV frame updated successfully"
        ));
    }
}
