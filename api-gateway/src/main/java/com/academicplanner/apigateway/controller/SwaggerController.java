package com.academicplanner.apigateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/swagger")
@Tag(name = "API Documentation", description = "Consolidated Swagger documentation access")
@SecurityRequirement(name = "bearerAuth")
public class SwaggerController {

    @Operation(
        summary = "Get available Swagger documentation links",
        description = "Returns links to Swagger UI for all microservices. Requires ADMIN role."
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved documentation links")
    @GetMapping("/services")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getSwaggerServices() {
        Map<String, Object> services = new HashMap<>();
        
        services.put("course-service", Map.of(
            "swagger-ui", "http://localhost:8080/swagger/course-service/",
            "api-docs", "http://localhost:8080/swagger/course-service-docs/",
            "direct-url", "http://localhost:8081/swagger-ui.html",
            "description", "Course Management Service - CRUD operations for academic courses"
        ));
        
        services.put("assignment-service", Map.of(
            "swagger-ui", "http://localhost:8080/swagger/assignment-service/",
            "api-docs", "http://localhost:8080/swagger/assignment-service-docs/",
            "direct-url", "http://localhost:8082/swagger-ui.html",
            "description", "Assignment Management Service - Student assignment tracking and management"
        ));
        
        services.put("resource-service", Map.of(
            "swagger-ui", "http://localhost:8080/swagger/resource-service/",
            "api-docs", "http://localhost:8080/swagger/resource-service-docs/",
            "direct-url", "http://localhost:8083/swagger-ui.html",
            "description", "Resource Management Service - Academic resource bookmarking and organization"
        ));
        
        services.put("gateway", Map.of(
            "swagger-ui", "http://localhost:8080/swagger-ui.html",
            "api-docs", "http://localhost:8080/v3/api-docs",
            "description", "API Gateway - Centralized routing and documentation"
        ));
        
        return ResponseEntity.ok(Map.of(
            "message", "Academic Planner Platform - API Documentation",
            "version", "1.0.0",
            "services", services,
            "authentication", "Bearer token required - Get token from Keycloak",
            "keycloak_token_url", "http://localhost:8180/auth/realms/GBC_Realm/protocol/openid-connect/token"
        ));
    }
}