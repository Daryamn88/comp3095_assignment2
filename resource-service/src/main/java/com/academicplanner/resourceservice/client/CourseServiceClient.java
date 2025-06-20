package com.academicplanner.resourceservice.client;

import com.academicplanner.resourceservice.dto.shared.CourseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class CourseServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(CourseServiceClient.class);
    
    private final WebClient webClient;
    
    public CourseServiceClient(WebClient.Builder webClientBuilder, 
                              @Value("${services.course-service.url:http://localhost:8081}") String courseServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(courseServiceUrl).build();
    }

    public List<String> getAllDepartments() {
        try {
            logger.info("Fetching all departments from course service");
            
            return webClient.get()
                    .uri("/api/courses/departments")
                    .retrieve()
                    .bodyToFlux(String.class)
                    .collectList()
                    .block();
        } catch (Exception e) {
            logger.error("Error fetching departments: {}", e.getMessage());
            return List.of();
        }
    }

    public List<CourseDto> getCoursesByDepartment(String department) {
        try {
            logger.info("Fetching courses for department: {}", department);
            
            return webClient.get()
                    .uri("/api/courses/department/{department}", department)
                    .retrieve()
                    .bodyToFlux(CourseDto.class)
                    .collectList()
                    .block();
        } catch (Exception e) {
            logger.error("Error fetching courses for department {}: {}", department, e.getMessage());
            return List.of();
        }
    }
}