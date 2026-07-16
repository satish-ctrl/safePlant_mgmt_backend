package com.safeops.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SensorRequest {
    @NotNull(message = "Sensor readings are required")
    @Size(min = 1, message = "At least one reading is required")
    private List<Object> readings;
}
