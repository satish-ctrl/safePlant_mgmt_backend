package com.safeops.backend.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SimulatorModeRequest {

    @NotBlank(message = "Simulation mode is required")
    private String simulationMode;

    @NotBlank(message = "Zone ID is required")
    private String zoneId;
}

