package com.academicplanner.assignmentservice.integration;

import com.academicplanner.assignmentservice.AssignmentServiceApplication;
import com.academicplanner.assignmentservice.entity.Assignment;
import com.academicplanner.assignmentservice.repository.AssignmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = AssignmentServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CircuitBreakerIntegrationTest {

    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:6.0.6")
            .withExposedPorts(27017);

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:23.0.0")
            .withRealmImportFile("keycloak/test-realm.json");

    private static WireMockServer wireMockServer;
    private static Keycloak keycloakAdmin;
    private static final String TEST_REALM = "test-realm";
    private static final String TEST_CLIENT_ID = "assignment-service";
    private static final String TEST_CLIENT_SECRET = "assignment-service-secret";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MongoDB configuration
        registry.add("spring.data.mongodb.uri", mongodb::getReplicaSetUrl);

        // WireMock configuration for Course Service
        wireMockServer = new WireMockServer(WireMockConfiguration.options()
                .port(8089)
                .withRootDirectory("src/test/resources"));
        wireMockServer.start();
        registry.add("services.course-service.url", () -> "http://localhost:8089");

        // Keycloak configuration
        String keycloakUrl = keycloak.getAuthServerUrl();
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> keycloakUrl + "/realms/" + TEST_REALM + "/protocol/openid-connect/certs");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> keycloakUrl + "/realms/" + TEST_REALM);
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri",
                () -> keycloakUrl + "/realms/" + TEST_REALM);

        // Circuit breaker test configuration - faster timeouts for testing
        registry.add("resilience4j.circuitbreaker.configs.default.sliding-window-size", () -> "5");
        registry.add("resilience4j.circuitbreaker.configs.default.minimum-number-of-calls", () -> "3");
        registry.add("resilience4j.circuitbreaker.configs.default.failure-rate-threshold", () -> "50");
        registry.add("resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state", () -> "5s");
        registry.add("resilience4j.circuitbreaker.configs.default.permitted-number-of-calls-in-half-open-state", () -> "2");

        registry.add("resilience4j.retry.configs.default.max-attempts", () -> "2");
        registry.add("resilience4j.retry.configs.default.wait-duration", () -> "500ms");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;
    private String adminToken;

    @BeforeAll
    static void setupKeycloak() {
        // Initialize Keycloak admin client and create test realm
        keycloakAdmin = KeycloakBuilder.builder()
                .serverUrl(keycloak.getAuthServerUrl())
                .realm("master")
                .clientId("admin-cli")
                .username(keycloak.getAdminUsername())
                .password(keycloak.getAdminPassword())
                .build();
    }

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/assignments";
        assignmentRepository.deleteAll();
        wireMockServer.resetAll();

        // Get admin token for tests
        adminToken = getAccessToken("admin", "admin123");

        // Reset circuit breaker state
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("courseService");
        circuitBreaker.reset();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.resetAll();
    }

    @AfterAll
    static void cleanup() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }

        if (keycloakAdmin != null) {
            keycloakAdmin.close();
        }
    }

    private String getAccessToken(String username, String password) {
        String tokenUrl = keycloak.getAuthServerUrl() + "/realms/" + TEST_REALM + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = String.format("grant_type=password&client_id=%s&client_secret=%s&username=%s&password=%s",
                TEST_CLIENT_ID, TEST_CLIENT_SECRET, username, password);

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("access_token");
    }

    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    @Order(1)
    void shouldUseFallbackWhenCourseServiceIsDown() {
        // Given - Course service is completely down
        wireMockServer.stop();

        Assignment assignment = new Assignment();
        assignment.setTitle("Fallback Test Assignment");
        assignment.setDueDate(LocalDateTime.now().plusDays(7));
        assignment.setCourseCode("CS101"); // Valid pattern
        assignment.setDescription("Testing fallback validation");

        HttpHeaders headers = createAuthHeaders(adminToken);
        HttpEntity<Assignment> request = new HttpEntity<>(assignment, headers);

        // When
        ResponseEntity<Assignment> response = restTemplate.exchange(
                baseUrl, HttpMethod.POST, request, Assignment.class);

        // Then - Should succeed with fallback validation
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Fallback Test Assignment");

        // Restart WireMock for other tests
        wireMockServer.start();
    }

    @Test
    @Order(2)
    void shouldRejectInvalidCourseCodePatternWithFallback() {
        // Given - Course service is down
        wireMockServer.stop();

        Assignment assignment = new Assignment();
        assignment.setTitle("Invalid Pattern Test");
        assignment.setDueDate(LocalDateTime.now().plusDays(7));
        assignment.setCourseCode("INVALID-CODE-123"); // Invalid pattern
        assignment.setDescription("Testing fallback validation with invalid pattern");

        HttpHeaders headers = createAuthHeaders(adminToken);
        HttpEntity<Assignment> request = new HttpEntity<>(assignment, headers);

        // When
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl, HttpMethod.POST, request, Map.class);

        // Then - Should fail due to invalid pattern
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").toString()).contains("Course code pattern appears invalid");

        // Restart WireMock
        wireMockServer.start();
    }

    @Test
    @Order(3)
    void shouldOpenCircuitBreakerAfterConsecutiveFailures() {
        // Given - Course service returns errors
        wireMockServer.stubFor(get(urlPathMatching("/api/courses/code/.*"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        Assignment assignment = new Assignment();
        assignment.setTitle("Circuit Breaker Test");
        assignment.setDueDate(LocalDateTime.now().plusDays(7));
        assignment.setDescription("Testing circuit breaker");

        HttpHeaders headers = createAuthHeaders(adminToken);

        // When - Make multiple failing requests to open circuit breaker
        for (int i = 1; i <= 5; i++) {
            assignment.setCourseCode("FAIL" + i);
            HttpEntity<Assignment> request = new HttpEntity<>(assignment, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl, HttpMethod.POST, request, Map.class);

            System.out.println("Request " + i + " status: " + response.getStatusCode());
        }

        // Then - Circuit breaker should be open
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("courseService");

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN)
        );

        // Additional request should use fallback immediately without calling service
        assignment.setCourseCode("CS999");
        HttpEntity<Assignment> fallbackRequest = new HttpEntity<>(assignment, headers);

        ResponseEntity<Assignment> fallbackResponse = restTemplate.exchange(
                baseUrl, HttpMethod.POST, fallbackRequest, Assignment.class);

        assertThat(fallbackResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify the service wasn't called (circuit breaker is open)
        // The exact count depends on retry configuration
        wireMockServer.verify(lessThan(10), getRequestedFor(urlPathMatching("/api/courses/code/.*")));
    }

    @Test
    @Order(4)
    void shouldRecoverWhenServiceReturnsToNormal() throws InterruptedException {
        // Given - Force circuit breaker to open state
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("courseService");
        circuitBreaker.transitionToOpenState();

        // Setup successful response
        wireMockServer.stubFor(get(urlEqualTo("/api/courses/code/CS101"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": 1,
                                    "courseCode": "CS101",
                                    "title": "Introduction to Programming",
                                    "description": "Basic programming concepts",
                                    "department": "Computer Science",
                                    "credits": 3
                                }
                                """)));

        // Wait for circuit breaker to transition to half-open
        Thread.sleep(6000); // Wait duration in open state + buffer

        Assignment assignment = new Assignment();
        assignment.setTitle("Recovery Test");
        assignment.setDueDate(LocalDateTime.now().plusDays(7));
        assignment.setCourseCode("CS101");
        assignment.setDescription("Testing circuit breaker recovery");

        HttpHeaders headers = createAuthHeaders(adminToken);
        HttpEntity<Assignment> request = new HttpEntity<>(assignment, headers);

        // When - Make successful requests to close circuit breaker
        for (int i = 0; i < 3; i++) {
            ResponseEntity<Assignment> response = restTemplate.exchange(
                    baseUrl, HttpMethod.POST, request, Assignment.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        // Then - Circuit breaker should be closed
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED)
        );
    }

    @Test
    @Order(5)
    void shouldHandleTimeoutWithCircuitBreaker() {
        // Given - Course service has high latency
        wireMockServer.stubFor(get(urlPathMatching("/api/courses/code/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(15000) // 15 second delay
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        Assignment assignment = new Assignment();
        assignment.setTitle("Timeout Test");
        assignment.setDueDate(LocalDateTime.now().plusDays(7));
        assignment.setCourseCode("SLOW123");
        assignment.setDescription("Testing timeout handling");

        HttpHeaders headers = createAuthHeaders(adminToken);
        HttpEntity<Assignment> request = new HttpEntity<>(assignment, headers);

        // When
        long startTime = System.currentTimeMillis();
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl, HttpMethod.POST, request, Map.class);
        long duration = System.currentTimeMillis() - startTime;

        // Then - Should timeout and use fallback
        assertThat(duration).isLessThan(12000); // Should not wait full 15 seconds
        assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(6)
    void shouldHandleNetworkErrorsGracefully() {
        // Given - Network errors
        wireMockServer.stubFor(get(urlPathMatching("/api/courses/code/.*"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        Assignment assignment = new Assignment();
        assignment.setTitle("Network Error Test");
        assignment.setDueDate(LocalDateTime.now().plusDays(7));
        assignment.setCourseCode("NET123");
        assignment.setDescription("Testing network error handling");

        HttpHeaders headers = createAuthHeaders(adminToken);
        HttpEntity<Assignment> request = new HttpEntity<>(assignment, headers);

        // When
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl, HttpMethod.POST, request, Map.class);

        // Then - Should handle gracefully with fallback
        assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(7)
    void shouldMonitorCircuitBreakerMetrics() {
        // Given
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("courseService");

        // Reset metrics
        circuitBreaker.reset();

        // Setup mixed responses
        wireMockServer.stubFor(get(urlEqualTo("/api/courses/code/SUCCESS1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"courseCode\":\"SUCCESS1\",\"title\":\"Test\"}")));

        wireMockServer.stubFor(get(urlEqualTo("/api/courses/code/FAIL1"))
                .willReturn(aResponse().withStatus(500)));

        HttpHeaders headers = createAuthHeaders(adminToken);

        // When - Make successful request
        Assignment successAssignment = new Assignment();
        successAssignment.setTitle("Success Metrics Test");
        successAssignment.setDueDate(LocalDateTime.now().plusDays(7));
        successAssignment.setCourseCode("SUCCESS1");
        successAssignment.setDescription("Testing metrics");

        restTemplate.exchange(baseUrl, HttpMethod.POST,
                new HttpEntity<>(successAssignment, headers), Assignment.class);

        // Make failing request
        Assignment failAssignment = new Assignment();
        failAssignment.setTitle("Fail Metrics Test");
        failAssignment.setDueDate(LocalDateTime.now().plusDays(7));
        failAssignment.setCourseCode("FAIL1");
        failAssignment.setDescription("Testing metrics");

        restTemplate.exchange(baseUrl, HttpMethod.POST,
                new HttpEntity<>(failAssignment, headers), Map.class);

        // Then - Check metrics
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();

        assertThat(metrics.getNumberOfBufferedCalls()).isGreaterThan(0);
        assertThat(metrics.getNumberOfSuccessfulCalls()).isGreaterThan(0);

        // Get circuit breaker status via actuator endpoint
        ResponseEntity<Map> statusResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/circuit-breaker/status/courseService",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusResponse.getBody()).containsKey("state");
        assertThat(statusResponse.getBody()).containsKey("failureRate");
    }
}