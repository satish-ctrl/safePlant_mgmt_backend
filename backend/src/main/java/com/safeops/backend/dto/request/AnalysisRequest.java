package com.safeops.backend.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AnalysisRequest {
    @NotBlank(message = "Zone ID is required")
    private String zoneId;

    private String currentTime;

    @DecimalMin(value = "0.0") @DecimalMax(value = "1.0")
    private double shiftRiskFactor = 0.1;

    @NotNull(message = "Sensor history is required")
    @Size(min = 1, message = "At least one sensor reading is required")
    private List<List<Double>> sensorRawHistory;

    @NotNull(message = "CV frame data is required")
    private Map<String, Object> cvRawFrame;

    private List<Map<String, Object>> activePermitsRaw;
}
