package com.safeops.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SafetyServiceTest {

    private MockWebServer mockWebServer;
    private SafetyService safetyService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        objectMapper = new ObjectMapper();
        WebClient.Builder builder = WebClient.builder();
        
        String baseUrl = mockWebServer.url("/").toString();
        safetyService = new SafetyService(builder, objectMapper, baseUrl, 5L);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testEvaluateSafetySuccess() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"event_id\": \"test-event-123\"}"));

        String ssePayload = "data: null\n\n" +
                "data: [\"{\\\"risk_fusion_out\\\": {\\\"score\\\": 0.15, \\\"severity\\\": \\\"LOW\\\"}, \\\"action_taken\\\": \\\"NONE\\\"}\"]\n\n";
        
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(ssePayload));

        Map<String, Object> mockState = Map.of("zone_id", "ZONE_3");

        Mono<Map<String, Object>> resultMono = safetyService.evaluateSafety(mockState);
        Map<String, Object> result = resultMono.block();

        assertNotNull(result);
        assertTrue(result.containsKey("risk_fusion_out"));
        Map<String, Object> riskFusion = (Map<String, Object>) result.get("risk_fusion_out");
        assertEquals(0.15, riskFusion.get("score"));
        assertEquals("LOW", riskFusion.get("severity"));
        assertEquals("NONE", result.get("action_taken"));

        RecordedRequest postRequest = mockWebServer.takeRequest();
        assertEquals("POST", postRequest.getMethod());
        assertEquals("/call/eval", postRequest.getPath());

        RecordedRequest getRequest = mockWebServer.takeRequest();
        assertEquals("GET", getRequest.getMethod());
        assertEquals("/call/eval/test-event-123", getRequest.getPath());
    }
}




