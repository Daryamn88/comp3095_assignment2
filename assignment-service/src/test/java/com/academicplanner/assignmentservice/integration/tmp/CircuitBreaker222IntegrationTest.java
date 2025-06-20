package com.academicplanner.assignmentservice.integration.tmp;

import com.academicplanner.assignmentservice.AssignmentServiceApplication;
import com.academicplanner.assignmentservice.client.CourseServiceClient;
import com.academicplanner.assignmentservice.repository.AssignmentRepository;
import com.academicplanner.assignmentservice.dto.shared.ValidationResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = AssignmentServiceApplication.class)
@Testcontainers
class CircuitBreaker222IntegrationTest {

    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:6.0.6")
            .withExposedPorts(27017);

    private static WireMockServer wireMockServer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongodb::getReplicaSetUrl);
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        registry.add("services.course-service.url", () -> "http://localhost:8089");
        
        // Configure circuit breaker for faster testing
        registry.add("resilience4j.circuitbreaker.instances.course-service.sliding-window-size", () -> "5");
        registry.add("resilience4j.circuitbreaker.instances.course-service.minimum-number-of-calls", () -> "3");
        registry.add("resilience4j.circuitbreaker.instances.course-service.failure-rate-threshold", () -> "60");
        registry.add("resilience4j.circuitbreaker.instances.course-service.wait-duration-in-open-state", () -> "10s");
        registry.add("resilience4j.retry.instances.course-service.max-attempts", () -> "2");
        registry.add("resilience4j.retry.instances.course-service.wait-duration", () -> "500ms");
    }

    @Autowired
    private CourseServiceClient courseServiceClient;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        assignmentRepository.deleteAll();
        wireMockServer.resetAll();
        
        // Reset circuit breaker state
        circuitBreakerRegistry.circuitBreaker("course-service").reset();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.resetAll();
        circuitBreakerRegistry.circuitBreaker("course-service").reset();
    }

    @Test
    void shouldSucceedWhenCourseServiceIsHealthy() {
        // Given: Course service is healthy and responds normally
        wireMockServer.stubFor(get(urlEqualTo("/api/courses/code/CS101"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "id": 1,
                                "courseCode": "CS101",
                                "title": "Introduction to Programming",
                                "department": "Computer Science",
                                "credits": 3
                            }
                            """)));

        // When: Validating course code
        ValidationResponse response = courseServiceClient.validateCourseCode("CS101");

        // Then: Should succeed
        assertThat(response.isValid()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Valid");
        
        // Verify circuit breaker is closed
        assertThat(circuitBreakerRegistry.circuitBreaker("course-service").getState())
                .isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldTriggerCircuitBreakerOnRepeatedFailures() {
        // Given: Course service returns 500 errors
        wireMockServer.stubFor(get(urlMatching("/api/courses/code/.*"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        // When: Making multiple calls that fail
        for (int i = 0; i < 5; i++) {
            try {
                courseServiceClient.validateCourseCode("CS101");
            } catch (Exception e) {
                // Expected to fail
            }
        }

        // Then: Circuit breaker should open
        await().until(() -> circuitBreakerRegistry.circuitBreaker("course-service").getState() 
                == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);

        // When: Making another call when circuit is open
        ValidationResponse fallbackResponse = courseServiceClient.validateCourseCode("CS101");

        // Then: Should get fallback response
        assertThat(fallbackResponse.isValid()).isFalse();
        assertThat(fallbackResponse.getMessage()).contains("temporarily unavailable");
        
        // Verify the fallback was called (no actual HTTP call made)
        verify(exactly(5), getRequestedFor(urlMatching("/api/courses/code/.*")));
    }

    @Test
    void shouldRecoverWhenServiceBecomesHealthyAgain() {
        // Given: Course service initially fails
        wireMockServer.stubFor(get(urlMatching("/api/courses/code/.*"))
                .willReturn(aResponse()
                        .withStatus(500)));

        // Trigger circuit breaker to open
        for (int i = 0; i < 5; i++) {
            try {
                courseServiceClient.validateCourseCode("CS101");
            } catch (Exception e) {
                // Expected to fail
            }
        }

        // Wait for circuit breaker to open
        await().until(() -> circuitBreakerRegistry.circuitBreaker("course-service").getState() 
                == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);

        // When: Service becomes healthy again
        wireMockServer.stubFor(get(urlEqualTo("/api/courses/code/CS101"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "id": 1,
                                "courseCode": "CS101",
                                "title": "Introduction to Programming",
                                "department": "Computer Science",
                                "credits": 3
                            }
                            """)));

        // Wait for circuit breaker to transition to half-open
        await().until(() -> circuitBreakerRegistry.circuitBreaker("course-service").getState() 
                == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.HALF_OPEN);

        // Then: Should eventually recover and work normally
        ValidationResponse response = courseServiceClient.validateCourseCode("CS101");
        assertThat(response.isValid()).isTrue();

        // Circuit breaker should close again
        await().until(() -> circuitBreakerRegistry.circuitBreaker("course-service").getState() 
                == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldRetryFailedRequestsBeforeOpeningCircuit() {
        // Given: Course service fails initially then succeeds
        wireMockServer.stubFor(get(urlEqualTo("/api/courses/code/CS101"))
                .inScenario("Retry Scenario")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                        .withStatus(500))
                .willSetStateTo("First Failure"));

        wireMockServer.stubFor(get(urlEqualTo("/api/courses/code/CS101"))
                .inScenario("Retry Scenario")
                .whenScenarioStateIs("First Failure")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "id": 1,
                                "courseCode": "CS101",
                                "title": "Introduction to Programming",
                                "department": "Computer Science",
                                "credits": 3
                            }
                            """)));

        // When: Making the call
        ValidationResponse response = courseServiceClient.validateCourseCode("CS101");

        // Then: Should succeed after retry
        assertThat(response.isValid()).isTrue();
        
        // Verify retry happened (2 requests: initial + 1 retry)
        verify(exactly(2), getRequestedFor(urlEqualTo("/api/courses/code/CS101")));
        
        // Circuit breaker should remain closed
        assertThat(circuitBreakerRegistry.circuitBreaker("course-service").getState())
                .isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldHandleTimeouts() {
        // Given: Course service responds very slowly
        wireMockServer.stubFor(get(urlEqualTo("/api/courses/code/CS101"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(6000) // 6 seconds - longer than timeout
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        // When: Making the call
        try {
            courseServiceClient.validateCourseCode("CS101");
        } catch (Exception e) {
            // Expected to timeout and fail
        }

        // Multiple timeout failures should trigger circuit breaker
        for (int i = 0; i < 3; i++) {
            try {
                courseServiceClient.validateCourseCode("CS101");
            } catch (Exception e) {
                // Expected
            }
        }

        // Then: Circuit breaker should eventually open due to timeouts
        await().until(() -> circuitBreakerRegistry.circuitBreaker("course-service").getState() 
                == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);
    }
}