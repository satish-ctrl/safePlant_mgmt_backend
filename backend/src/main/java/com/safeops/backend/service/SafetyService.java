package com.safeops.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
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
                         @Value("${safeops.safety-engine.base-url:https://monkey3770-safeops-ai-engine.hf.space/gradio_api}") String baseUrl,
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
                    .flatMap(this::pollForResult)
                    .onErrorMap(WebClientResponseException.class, ex -> {
                        log.warn("Safety engine returned HTTP {}: {}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
                        return ex;
                    });
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    /**
     * Polls the Gradio event endpoint until the result is ready.
     * The initial response contains an event_id; we poll /call/eval/{event_id} to get the actual data.
     */
    private Mono<Map<String, Object>> pollForResult(Map<String, Object> initialResponse) {
        String eventId = extractEventId(initialResponse);
        if (eventId == null) {
            return Mono.just(initialResponse);
        }
        log.debug("Polling Gradio event for event_id: {}", eventId);
        return pollEventEndpoint(eventId);
    }

    /**
     * Connects to the Gradio event endpoint and streams events until the result is ready.
     * Uses Server-Sent Events (SSE) streaming for non-blocking real-time results.
     */
    private Mono<Map<String, Object>> pollEventEndpoint(String eventId) {
        ParameterizedTypeReference<ServerSentEvent<String>> typeRef = new ParameterizedTypeReference<>() {};

        return this.webClient.get()
                .uri("/call/eval/" + eventId)
                .retrieve()
                .bodyToFlux(typeRef)
                .timeout(requestTimeout)
                .filter(event -> {
                    if ("error".equals(event.event())) {
                        throw new RuntimeException("Gradio execution error: " + event.data());
                    }
                    return event.data() != null && !"null".equals(event.data());
                })
                .map(event -> {
                    try {
                        List<String> dataList = objectMapper.readValue(event.data(), new TypeReference<List<String>>() {});
                        if (!dataList.isEmpty()) {
                            return parseResultData(dataList.get(0));
                        }
                        throw new RuntimeException("Empty data list in Gradio event");
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse Gradio event data: " + event.data(), e);
                    }
                })
                .next() // Take the first parsed result event and complete the Mono
                .onErrorResume(ex -> {
                    log.warn("Error streaming from Gradio event endpoint: {}", ex.getMessage());
                    return Mono.error(ex);
                });
    }

    private String extractEventId(Map<String, Object> response) {
        if (response.containsKey("event_id")) {
            Object eventId = response.get("event_id");
            return eventId != null ? eventId.toString() : null;
        }
        Object data = response.get("data");
        if (data instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof String str && str.contains("-")) {
                return str; 
            }
            if (first instanceof Map<?, ?> map && map.containsKey("event_id")) {
                Object eventId = map.get("event_id");
                return eventId != null ? eventId.toString() : null;
            }
        }
        return null;
    }
    private Map<String, Object> parseResultData(Object data) {
        if (data instanceof String json) {
            try {
                return objectMapper.readValue(json, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Failed to parse Gradio result as JSON: {}", json);
                return Map.of("raw_result", json);
            }
        }
        if (data instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, new TypeReference<>() {});
        }
        return Map.of("result", data);
    }
}




