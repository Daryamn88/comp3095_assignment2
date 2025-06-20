package com.academicplanner.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Course Service Routes
                .route("course-service", r -> r
                        .path("/api/courses/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway-Request", "true")
                                .addResponseHeader("X-Gateway-Response", "course-service"))
                        .uri("lb://course-service"))
                
                // Assignment Service Routes
                .route("assignment-service", r -> r
                        .path("/api/assignments/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway-Request", "true")
                                .addResponseHeader("X-Gateway-Response", "assignment-service"))
                        .uri("lb://assignment-service"))
                
                // Resource Service Routes
                .route("resource-service", r -> r
                        .path("/api/resources/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway-Request", "true")
                                .addResponseHeader("X-Gateway-Response", "resource-service"))
                        .uri("lb://resource-service"))
                
                // Swagger UI Routes for individual services
                .route("course-swagger-ui", r -> r
                        .path("/swagger/course-service/**")
                        .filters(f -> f.rewritePath("/swagger/course-service/(?<path>.*)", "/swagger-ui/${path}"))
                        .uri("lb://course-service"))
                
                .route("course-api-docs", r -> r
                        .path("/swagger/course-service-docs/**")
                        .filters(f -> f.rewritePath("/swagger/course-service-docs/(?<path>.*)", "/api-docs/${path}"))
                        .uri("lb://course-service"))
                
                .route("assignment-swagger-ui", r -> r
                        .path("/swagger/assignment-service/**")
                        .filters(f -> f.rewritePath("/swagger/assignment-service/(?<path>.*)", "/swagger-ui/${path}"))
                        .uri("lb://assignment-service"))
                
                .route("assignment-api-docs", r -> r
                        .path("/swagger/assignment-service-docs/**")
                        .filters(f -> f.rewritePath("/swagger/assignment-service-docs/(?<path>.*)", "/api-docs/${path}"))
                        .uri("lb://assignment-service"))
                
                .route("resource-swagger-ui", r -> r
                        .path("/swagger/resource-service/**")
                        .filters(f -> f.rewritePath("/swagger/resource-service/(?<path>.*)", "/swagger-ui/${path}"))
                        .uri("lb://resource-service"))
                
                .route("resource-api-docs", r -> r
                        .path("/swagger/resource-service-docs/**")
                        .filters(f -> f.rewritePath("/swagger/resource-service-docs/(?<path>.*)", "/api-docs/${path}"))
                        .uri("lb://resource-service"))
                
                // Eureka Dashboard Route (for monitoring)
                .route("eureka-server", r -> r
                        .path("/eureka/**")
                        .uri("lb://eureka-server"))
                
                // Gateway Actuator Routes
                .route("gateway-actuator", r -> r
                        .path("/actuator/**")
                        .uri("forward:/actuator"))
                
                .build();
    }
}