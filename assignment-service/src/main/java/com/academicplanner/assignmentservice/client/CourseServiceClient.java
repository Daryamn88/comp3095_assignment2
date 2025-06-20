package com.academicplanner.assignmentservice.client;

import com.academicplanner.assignmentservice.dto.shared.CourseDto;
import com.academicplanner.assignmentservice.dto.shared.ValidationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class CourseServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(CourseServiceClient.class);
    private static final String COURSE_SERVICE_CB = "courseService";

    private final WebClient webClient;

    @Value("${services.course-service.url:http://localhost:8081}")
    private String courseServiceUrl;

    @CircuitBreaker(name = COURSE_SERVICE_CB, fallbackMethod = "validateCourseCodeFallback")
    @Retry(name = COURSE_SERVICE_CB)
    @TimeLimiter(name = COURSE_SERVICE_CB)
    public CompletableFuture<ValidationResponse> validateCourseCodeAsync(String courseCode) {
        return CompletableFuture.supplyAsync(() -> validateCourseCodeSync(courseCode));
    }

    @CircuitBreaker(name = COURSE_SERVICE_CB, fallbackMethod = "validateCourseCodeFallback")
    @Retry(name = COURSE_SERVICE_CB)
    public ValidationResponse validateCourseCode(String courseCode) {
        return validateCourseCodeSync(courseCode);
    }

    private ValidationResponse validateCourseCodeSync(String courseCode) {
        try {
            logger.info("Validating course code: {} with course service", courseCode);

            CourseDto course = webClient.get()
                    .uri(courseServiceUrl + "/api/courses/code/{courseCode}", courseCode)
                    .attributes(org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId("keycloak"))
                    .retrieve()
                    .bodyToMono(CourseDto.class)
                    .block();

            if (course != null) {
                logger.info("Course code {} is valid: {}", courseCode, course.getTitle());
                return ValidationResponse.valid("course");
            } else {
                logger.warn("Course code {} not found", courseCode);
                return ValidationResponse.invalid("Course code not found", "course");
            }
        } catch (WebClientException e) {
            logger.error("WebClient error validating course code {}: {}", courseCode, e.getMessage());
            throw e; // Let circuit breaker handle it
        } catch (Exception e) {
            logger.error("Unexpected error validating course code {}: {}", courseCode, e.getMessage());
            throw e; // Let circuit breaker handle it
        }
    }

    @CircuitBreaker(name = COURSE_SERVICE_CB, fallbackMethod = "getCourseByCcodeFallback")
    @Retry(name = COURSE_SERVICE_CB)
    public CourseDto getCourseByCode(String courseCode) {
        try {
            logger.info("Fetching course details for code: {}", courseCode);

            return webClient.get()
                    .uri(courseServiceUrl + "/api/courses/code/{courseCode}", courseCode)
                    .attributes(org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId("keycloak"))
                    .retrieve()
                    .bodyToMono(CourseDto.class)
                    .block();
        } catch (Exception e) {
            logger.error("Error fetching course by code {}: {}", courseCode, e.getMessage());
            throw e; // Let circuit breaker handle it
        }
    }

    // Fallback methods
    public ValidationResponse validateCourseCodeFallback(String courseCode, Exception ex) {
        logger.warn("Course validation fallback triggered for course code: {} due to: {}",
                courseCode, ex.getMessage());

        // Check if it's a common course code pattern (basic validation)
        if (isValidCourseCodePattern(courseCode)) {
            logger.info("Using pattern-based validation for course code: {}", courseCode);
            return ValidationResponse.valid("course");
        }

        return ValidationResponse.invalid(
                "Course service unavailable - using fallback validation. Course code pattern appears invalid.",
                "course"
        );
    }

    public CompletableFuture<ValidationResponse> validateCourseCodeFallback(String courseCode, TimeoutException ex) {
        logger.warn("Course validation timeout fallback triggered for course code: {}", courseCode);
        return CompletableFuture.completedFuture(
                ValidationResponse.invalid("Course service timeout - please try again later", "course")
        );
    }

    public CourseDto getCourseByCcodeFallback(String courseCode, Exception ex) {
        logger.warn("Course fetch fallback triggered for course code: {} due to: {}",
                courseCode, ex.getMessage());

        // Return a minimal course object with just the course code
        CourseDto fallbackCourse = new CourseDto();
        fallbackCourse.setCourseCode(courseCode);
        fallbackCourse.setTitle("Course details unavailable");
        fallbackCourse.setDescription("Course service is currently unavailable. Please try again later.");
        fallbackCourse.setDepartment("Unknown");
        fallbackCourse.setCredits(0);

        return fallbackCourse;
    }

    private boolean isValidCourseCodePattern(String courseCode) {
        // Basic pattern validation: 2-4 letters followed by 3 digits
        return courseCode != null && courseCode.matches("^[A-Z]{2,4}\\d{3}$");
    }
}