package com.academicplanner.apigateway.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi gatewayApi() {
        return GroupedOpenApi.builder()
                .group("gateway")
                .pathsToMatch("/gateway/**")
                .build();
    }

    @Bean
    public List<GroupedOpenApi> apis(RouteDefinitionLocator locator) {
        var groups = new ArrayList<GroupedOpenApi>();
        
        // Add grouped APIs for each service
        groups.add(GroupedOpenApi.builder()
                .group("course-service")
                .pathsToMatch("/api/courses/**")
                .build());
        
        groups.add(GroupedOpenApi.builder()
                .group("assignment-service")
                .pathsToMatch("/api/assignments/**")
                .build());
        
        groups.add(GroupedOpenApi.builder()
                .group("resource-service")
                .pathsToMatch("/api/resources/**")
                .build());
        
        return groups;
    }
}