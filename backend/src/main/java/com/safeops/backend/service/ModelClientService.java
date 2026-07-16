package com.safeops.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * Service responsible for calling the Python FastAPI model service.
 * Includes retry logic and circuit breaker for resilience.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelClientService {

    private final WebClient modelServiceWebClient;
    private final ObjectMapper objectMapper;

    @CircuitBreaker(name = "modelService", fallbackMethod = "analysisFallback")
    @Retry(name = "modelService")
    public Map<String, Object> runFullAnalysis(Map<String, Object> request) {
        log.info("Calling model service /analyze for zone: {}", request.get("zone_id"));
        return modelServiceWebClient.post()
                .uri("/analyze")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(15))
                .block();
    }

    @CircuitBreaker(name = "modelService", fallbackMethod = "sensorFallback")
    @Retry(name = "modelService")
    public Map<String, Object> checkSensorAnomaly(Map<String, Object> request) {
        log.info("Calling model service /sensor/anomaly");
        return modelServiceWebClient.post()
                .uri("/sensor/anomaly")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .block();
    }

    @CircuitBreaker(name = "modelService", fallbackMethod = "permitFallback")
    @Retry(name = "modelService")
    public Map<String, Object> checkPermitConflicts(Map<String, Object> request) {
        log.info("Calling model service /permits/check");
        return modelServiceWebClient.post()
                .uri("/permits/check")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .block();
    }

    @CircuitBreaker(name = "modelService", fallbackMethod = "ragFallback")
    @Retry(name = "modelService")
    public Map<String, Object> queryRAG(Map<String, Object> request) {
        log.info("Calling model service /rag/query");
        return modelServiceWebClient.post()
                .uri("/rag/query")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .block();
    }

    public Map<String, Object> checkHealth() {
        return modelServiceWebClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(3))
                .block();
    }

    // ─── Fallback methods ──────────────────────────────────────────────────

    @SuppressWarnings("unused")
    private Map<String, Object> analysisFallback(Map<String, Object> request, Throwable t) {
        log.error("Model service /analyze call failed, circuit breaker triggered: {}", t.getMessage());
        return Map.of(
                "error", "MODEL_SERVICE_UNAVAILABLE",
                "message", "The AI analysis service is temporarily unavailable. Please try again later.",
                "fallback", true
        );
    }

    @SuppressWarnings("unused")
    private Map<String, Object> sensorFallback(Map<String, Object> request, Throwable t) {
        log.error("Model service /sensor/anomaly call failed: {}", t.getMessage());
        return Map.of("error", "MODEL_SERVICE_UNAVAILABLE", "message", "Sensor analysis unavailable.", "fallback", true);
    }

    @SuppressWarnings("unused")
    private Map<String, Object> permitFallback(Map<String, Object> request, Throwable t) {
        log.error("Model service /permits/check call failed: {}", t.getMessage());
        return Map.of("error", "MODEL_SERVICE_UNAVAILABLE", "message", "Permit check unavailable.", "fallback", true);
    }

    @SuppressWarnings("unused")
    private Map<String, Object> ragFallback(Map<String, Object> request, Throwable t) {
        log.error("Model service /rag/query call failed: {}", t.getMessage());
        return Map.of("error", "MODEL_SERVICE_UNAVAILABLE", "message", "RAG query unavailable.", "fallback", true);
    }
}
