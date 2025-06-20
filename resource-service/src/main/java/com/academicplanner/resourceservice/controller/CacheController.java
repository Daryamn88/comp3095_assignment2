package com.academicplanner.resourceservice.controller;

import com.academicplanner.resourceservice.service.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/cache")
@Tag(name = "Cache Management", description = "Manage cached data for fault tolerance")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CacheController {

    private final ResourceService resourceService;

    @Operation(
            summary = "Get cache statistics",
            description = "Retrieve statistics about cached data. Requires ADMIN role."
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved cache statistics")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> stats = resourceService.getCacheStats();
        return ResponseEntity.ok(stats);
    }

    @Operation(
            summary = "Clear all cached data",
            description = "Clear all cached data to force fresh fetches. Requires ADMIN role."
    )
    @PostMapping("/clear")
    public ResponseEntity<Map<String, String>> clearCache() {
        resourceService.clearCache();
        return ResponseEntity.ok(Map.of(
                "message", "Cache cleared successfully",
                "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
}