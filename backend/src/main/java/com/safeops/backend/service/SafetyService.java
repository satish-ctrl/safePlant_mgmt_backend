package com.safeops.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import org.springframework.core.ParameterizedTypeReference;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SafetyService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;

    public SafetyService(WebClient.Builder webClientBuilder,
                         ObjectMapper objectMapper,
                         @Value("${safeops.safety-engine.base-url:https://monkey3770-safeops-ai-engine.hf.space}") String baseUrl,
                         @Value("${safeops.safety-engine.timeout-seconds:20}") long timeoutSeconds) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
    }

    public Mono<Map<String, Object>> evaluateSafety(Map<String, Object> statePayload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(statePayload);
            Map<String, Object> gradioPayload = Map.of("data", List.of(jsonPayload));

            return this.webClient.post()
                    .uri("/call/eval")
                    .bodyValue(gradioPayload)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(requestTimeout)
                    .map(this::extractResultFromGradioResponse)
                    .onErrorMap(WebClientResponseException.class, ex -> {
                        log.warn("Safety engine returned HTTP {}: {}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
                        return ex;
                    });
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private Map<String, Object> extractResultFromGradioResponse(Map<String, Object> response) {
        Object data = response.get("data");
        if (data instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof String json) {
                try {
                    return objectMapper.readValue(json, new TypeReference<>() {});
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to parse Gradio response payload", e);
                }
            }
            if (first instanceof Map<?, ?> map) {
                return objectMapper.convertValue(map, new TypeReference<>() {});
            }
        }
        return response;
    }
}




