package com.safeops.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data @Builder @AllArgsConstructor
public class JobStatusResponse {
    private String jobId;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
    private Map<String, Object> result;
    private String errorMessage;
    private Instant createdAt;
    private Instant completedAt;
    private Long latencyMs;
}
