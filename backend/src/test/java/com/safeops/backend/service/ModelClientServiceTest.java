package com.safeops.backend.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModelClientServiceTest {

    private static MockWebServer mockWebServer;
    private ModelClientService modelClientService;

    @BeforeAll
    static void setUpServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @BeforeEach
    void setUp() {
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        modelClientService = new ModelClientService(webClient, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void checkSensorAnomaly_shouldReturnResult() {
        String responseBody = """
                {
                    "agent": "sensor_anomaly",
                    "anomaly_score": 0.85,
                    "severity": "HIGH",
                    "current_value": 32.0
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = modelClientService.checkSensorAnomaly(
                Map.of("readings", List.of(List.of(32.0, 29.0, 1.2)))
        );

        assertThat(result).containsKey("agent");
        assertThat(result.get("agent")).isEqualTo("sensor_anomaly");
    }

    @Test
    void checkPermitConflicts_shouldReturnResult() {
        String responseBody = """
                {
                    "agent": "permit_intel",
                    "conflicts": [],
                    "timestamp": "2026-07-01T12:00:00Z"
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = modelClientService.checkPermitConflicts(
                Map.of("permits", List.of(), "current_time", "", "current_gas", 0.0)
        );

        assertThat(result).containsKey("agent");
        assertThat(result.get("agent")).isEqualTo("permit_intel");
    }

    @Test
    void checkHealth_shouldReturnHealthyStatus() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"status\": \"healthy\", \"model_loaded\": true, \"chromadb_ready\": true}")
                .addHeader("Content-Type", "application/json"));

        Map<String, Object> result = modelClientService.checkHealth();

        assertThat(result.get("status")).isEqualTo("healthy");
    }
}
