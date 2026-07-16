package com.safeops.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safeops.backend.dto.request.AnalysisRequest;
import com.safeops.backend.entity.AnalysisJob;
import com.safeops.backend.repository.AnalysisJobRepository;
import com.safeops.backend.repository.InvocationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock private ModelClientService modelClientService;
    @Mock private AnalysisJobRepository analysisJobRepository;
    @Mock private InvocationLogRepository invocationLogRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private AnalysisService analysisService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Re-inject with real ObjectMapper
        analysisService = new AnalysisService(
                modelClientService, analysisJobRepository, invocationLogRepository,
                redisTemplate, objectMapper
        );
    }

    @Test
    void submitAnalysis_shouldCreateJobAndReturnId() {
        AnalysisRequest request = new AnalysisRequest();
        request.setZoneId("ZONE_3");
        request.setShiftRiskFactor(0.1);
        request.setSensorRawHistory(List.of(List.of(18.0, 28.0, 1.2)));
        request.setCvRawFrame(Map.of("zone_id", "ZONE_3", "workers_detected", 2, "violations", List.of()));

        when(analysisJobRepository.save(any(AnalysisJob.class))).thenAnswer(inv -> inv.getArgument(0));

        String jobId = analysisService.submitAnalysis(request, 1L);

        assertThat(jobId).isNotNull().isNotEmpty();
        verify(analysisJobRepository, atLeastOnce()).save(any(AnalysisJob.class));
    }

    @Test
    void getJobStatus_shouldReturnFromDatabase() {
        AnalysisJob job = AnalysisJob.builder()
                .jobId("test-job-id")
                .userId(1L)
                .status("COMPLETED")
                .resultPayload(Map.of("score", 0.85))
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(analysisJobRepository.findById("test-job-id")).thenReturn(Optional.of(job));

        var status = analysisService.getJobStatus("test-job-id");

        assertThat(status.getJobId()).isEqualTo("test-job-id");
        assertThat(status.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void getJobStatus_shouldThrowForMissingJob() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(analysisJobRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analysisService.getJobStatus("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Job not found");
    }
}
