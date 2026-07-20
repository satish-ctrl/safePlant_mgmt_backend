package com.safeops.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Thin proxy for the model service endpoints used by controllers/health checks.
 * This replaces the previous, larger ModelClientService implementation while keeping
 * the same observable behavior for the REST controllers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelProxyService {

    private final WebClient modelServiceWebClient;

    public Map<String, Object> checkSensorAnomaly(Map<String, Object> request) {
        log.debug("Proxying sensor anomaly request to model service");
        return modelServiceWebClient.post()
                .uri("/sensor/anomaly")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .block();
    }

    public Map<String, Object> checkPermitConflicts(Map<String, Object> request) {
        log.debug("Proxying permit check request to model service");
        return modelServiceWebClient.post()
                .uri("/permits/check")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .block();
    }

    public Map<String, Object> checkHealth() {
        log.debug("Proxying health check to model service");
        return modelServiceWebClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(3))
                .block();
    }
}

