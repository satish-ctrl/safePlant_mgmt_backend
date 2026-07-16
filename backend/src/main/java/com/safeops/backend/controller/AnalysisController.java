package com.safeops.backend.controller;

import com.safeops.backend.dto.request.AnalysisRequest;
import com.safeops.backend.dto.response.JobStatusResponse;
import com.safeops.backend.entity.User;
import com.safeops.backend.repository.UserRepository;
import com.safeops.backend.service.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
@Tag(name = "Analysis", description = "AI safety analysis endpoints (async job pattern)")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final UserRepository userRepository;

    @PostMapping("/submit")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Submit a full analysis job (async)")
    public ResponseEntity<Map<String, String>> submitAnalysis(
            @Valid @RequestBody AnalysisRequest request,
            Authentication authentication) {

        Long userId = getUserId(authentication);
        String jobId = analysisService.submitAnalysis(request, userId);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "jobId", jobId,
                        "status", "PENDING",
                        "message", "Analysis job submitted. Poll /api/v1/analysis/status/" + jobId + " for results."
                ));
    }

    @GetMapping("/status/{jobId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Check analysis job status and results")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(analysisService.getJobStatus(jobId));
    }

    private Long getUserId(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.getId();
    }
}
