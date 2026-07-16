package com.safeops.backend.controller;

import com.safeops.backend.repository.InvocationLogRepository;
import com.safeops.backend.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administrative endpoints (ADMIN role required)")
public class AdminController {

    private final UserRepository userRepository;
    private final InvocationLogRepository invocationLogRepository;

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users")
    public ResponseEntity<?> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userRepository.findAll(PageRequest.of(page, size)));
    }

    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "View invocation logs")
    public ResponseEntity<?> viewLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(invocationLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)));
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "System metrics summary")
    public ResponseEntity<Map<String, Object>> metrics() {
        long totalUsers = userRepository.count();
        long totalInvocations = invocationLogRepository.count();
        return ResponseEntity.ok(Map.of(
                "totalUsers", totalUsers,
                "totalInvocations", totalInvocations
        ));
    }
}
