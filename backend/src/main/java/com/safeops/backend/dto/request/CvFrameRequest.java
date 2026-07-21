package com.safeops.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload submitted by live video analytics / YOLO edge node")
public class CvFrameRequest {

    @NotBlank(message = "Zone ID is required")
    @Schema(example = "ZONE_3")
    private String zoneId;

    @Schema(example = "3")
    private int workersDetected;

    @Schema(description = "List of detected safety violations (NO_HELMET, NO_VEST, etc.)")
    private List<Map<String, Object>> violations;
}
