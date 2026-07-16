package com.safeops.backend.controller;

import com.safeops.backend.dto.request.PermitCheckRequest;
import com.safeops.backend.service.ModelClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/permits")
@RequiredArgsConstructor
@Tag(name = "Permits", description = "Work permit conflict checking endpoints")
public class PermitController {

    private final ModelClientService modelClientService;

    @PostMapping("/check")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Check work permits for conflicts (synchronous)")
    public ResponseEntity<Map<String, Object>> checkPermits(@Valid @RequestBody PermitCheckRequest request) {
        Map<String, Object> result = modelClientService.checkPermitConflicts(
                Map.of(
                        "permits", request.getPermits(),
                        "current_time", request.getCurrentTime() != null ? request.getCurrentTime() : "",
                        "current_gas", request.getCurrentGas()
                )
        );
        return ResponseEntity.ok(result);
    }
}
