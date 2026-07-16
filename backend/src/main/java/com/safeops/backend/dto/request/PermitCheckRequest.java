package com.safeops.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PermitCheckRequest {
    @NotNull(message = "Permits list is required")
    @Size(min = 1, message = "At least one permit is required")
    private List<Map<String, Object>> permits;

    private String currentTime;

    @DecimalMin(value = "0.0")
    private double currentGas = 0.0;
}
