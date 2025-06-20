package com.academicplanner.assignmentservice.controller;

import com.academicplanner.assignmentservice.service.CircuitBreakerMonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/circuit-breaker")
@Tag(name = "Circuit Breaker Monitoring", description = "Monitor and control circuit breaker status")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CircuitBreakerController {
    private final CircuitBreakerMonitoringService monitoringService;

    @Operation(
        summary = "Get all circuit breakers status",
        description = "Retrieve status information for all registered circuit breakers. Requires ADMIN role."
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved circuit breaker status")
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllStatus() {
        Map<String, Object> status = monitoringService.getAllCircuitBreakersStatus();
        return ResponseEntity.ok(status);
    }

    @Operation(
        summary = "Get specific circuit breaker status",
        description = "Retrieve status information for a specific circuit breaker"
    )
    @GetMapping("/status/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStatus(
            @Parameter(description = "Circuit breaker name", required = true, example = "courseService")
            @PathVariable String name) {
        Map<String, Object> status = monitoringService.getCircuitBreakerStatus(name);
        return ResponseEntity.ok(status);
    }

    @Operation(
        summary = "Reset circuit breaker",
        description = "Reset a circuit breaker to closed state. Requires ADMIN role."
    )
    @PostMapping("/reset/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> resetCircuitBreaker(
            @Parameter(description = "Circuit breaker name", required = true, example = "courseService")
            @PathVariable String name) {
        monitoringService.resetCircuitBreaker(name);
        return ResponseEntity.ok(Map.of(
            "message", "Circuit breaker reset successfully",
            "circuitBreaker", name,
            "newState", "CLOSED"
        ));
    }

    @Operation(
        summary = "Transition circuit breaker to open state",
        description = "Manually open a circuit breaker for testing. Requires ADMIN role."
    )
    @PostMapping("/open/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> openCircuitBreaker(
            @Parameter(description = "Circuit breaker name", required = true, example = "courseService")
            @PathVariable String name) {
        monitoringService.transitionToOpenState(name);
        return ResponseEntity.ok(Map.of(
            "message", "Circuit breaker opened successfully",
            "circuitBreaker", name,
            "newState", "OPEN"
        ));
    }

    @Operation(
        summary = "Transition circuit breaker to closed state",
        description = "Manually close a circuit breaker. Requires ADMIN role."
    )
    @PostMapping("/close/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> closeCircuitBreaker(
            @Parameter(description = "Circuit breaker name", required = true, example = "courseService")
            @PathVariable String name) {
        monitoringService.transitionToClosedState(name);
        return ResponseEntity.ok(Map.of(
            "message", "Circuit breaker closed successfully",
            "circuitBreaker", name,
            "newState", "CLOSED"
        ));
    }
}