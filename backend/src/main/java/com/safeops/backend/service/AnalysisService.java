package com.safeops.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeops.backend.dto.request.AnalysisRequest;
import com.safeops.backend.dto.response.JobStatusResponse;
import com.safeops.backend.entity.AnalysisJob;
import com.safeops.backend.entity.InvocationLog;
import com.safeops.backend.repository.AnalysisJobRepository;
import com.safeops.backend.repository.InvocationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private final ModelClientService modelClientService;
    private final AnalysisJobRepository analysisJobRepository;
    private final InvocationLogRepository invocationLogRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Submits an analysis job — stores it as PENDING and triggers async processing.
     */
    @Transactional
    public String submitAnalysis(AnalysisRequest request, Long userId) {
        String jobId = UUID.randomUUID().toString();

        Map<String, Object> requestMap = objectMapper.convertValue(request, Map.class);

        AnalysisJob job = AnalysisJob.builder()
                .jobId(jobId)
                .userId(userId)
                .status("PENDING")
                .requestPayload(requestMap)
                .build();

        analysisJobRepository.save(job);
        log.info("Analysis job {} created for user {}", jobId, userId);

        processAnalysisAsync(jobId, request, userId);

        return jobId;
    }

    /**
     * Retrieves the current status and results of an analysis job.
     */
    public JobStatusResponse getJobStatus(String jobId) {
        // Check Redis cache first
        Object cached = redisTemplate.opsForValue().get("job:" + jobId);
        if (cached != null) {
            return objectMapper.convertValue(cached, JobStatusResponse.class);
        }

        AnalysisJob job = analysisJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        JobStatusResponse response = JobStatusResponse.builder()
                .jobId(job.getJobId())
                .status(job.getStatus())
                .result(job.getResultPayload())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .latencyMs(job.getLatencyMs())
                .build();

        // Cache completed results for 5 minutes
        if ("COMPLETED".equals(job.getStatus()) || "FAILED".equals(job.getStatus())) {
            redisTemplate.opsForValue().set("job:" + jobId, response, Duration.ofMinutes(5));
        }

        return response;
    }

    @Async("analysisExecutor")
    @Transactional
    public void processAnalysisAsync(String jobId, AnalysisRequest request, Long userId) {
        log.info("Processing analysis job {} asynchronously", jobId);
        long startTime = System.currentTimeMillis();

        AnalysisJob job = analysisJobRepository.findById(jobId).orElse(null);
        if (job == null) return;

        job.setStatus("PROCESSING");
        analysisJobRepository.save(job);

        try {
            // Build request for the Python model service
            Map<String, Object> modelRequest = Map.of(
                    "zone_id", request.getZoneId(),
                    "current_time", request.getCurrentTime() != null ? request.getCurrentTime() : "",
                    "shift_risk_factor", request.getShiftRiskFactor(),
                    "sensor_raw_history", request.getSensorRawHistory(),
                    "cv_raw_frame", request.getCvRawFrame(),
                    "active_permits_raw", request.getActivePermitsRaw() != null ? request.getActivePermitsRaw() : java.util.List.of()
            );

            Map<String, Object> result = modelClientService.runFullAnalysis(modelRequest);

            long latencyMs = System.currentTimeMillis() - startTime;

            job.setStatus("COMPLETED");
            job.setResultPayload(result);
            job.setLatencyMs(latencyMs);
            job.setCompletedAt(Instant.now());
            analysisJobRepository.save(job);

            // Log the invocation
            InvocationLog logEntry = InvocationLog.builder()
                    .userId(userId)
                    .endpoint("/analyze")
                    .requestBody(job.getRequestPayload())
                    .responseBody(result)
                    .statusCode(200)
                    .latencyMs(latencyMs)
                    .build();
            invocationLogRepository.save(logEntry);

            log.info("Analysis job {} completed in {}ms", jobId, latencyMs);

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("Analysis job {} failed after {}ms: {}", jobId, latencyMs, e.getMessage());

            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setLatencyMs(latencyMs);
            job.setCompletedAt(Instant.now());
            analysisJobRepository.save(job);
        }
    }
}
