package com.safeops.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulatorStatusResponse {
    private String simulationMode;
    private String zoneId;
    private Double shiftRiskFactor;
    private Integer bufferedReadings;
    private String sensorId;
    private List<Double> latestReading;
    private Instant latestReadingAt;
}

