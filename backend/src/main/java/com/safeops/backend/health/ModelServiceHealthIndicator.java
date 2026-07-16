package com.safeops.backend.health;

import com.safeops.backend.service.ModelClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ModelServiceHealthIndicator implements HealthIndicator {

    private final ModelClientService modelClientService;

    @Override
    public Health health() {
        try {
            Map<String, Object> healthResult = modelClientService.checkHealth();
            if ("healthy".equals(healthResult.get("status"))) {
                return Health.up()
                        .withDetail("modelLoaded", healthResult.get("model_loaded"))
                        .withDetail("chromaDbReady", healthResult.get("chromadb_ready"))
                        .build();
            }
            return Health.down()
                    .withDetail("status", healthResult.get("status"))
                    .build();
        } catch (Exception e) {
            log.warn("Model service health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
