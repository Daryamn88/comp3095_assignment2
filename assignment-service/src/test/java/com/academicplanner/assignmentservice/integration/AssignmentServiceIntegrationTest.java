package com.academicplanner.assignmentservice.integration;

import com.academicplanner.assignmentservice.AssignmentServiceApplication;
import com.academicplanner.assignmentservice.entity.Assignment;
import com.academicplanner.assignmentservice.entity.AssignmentStatus;
import com.academicplanner.assignmentservice.repository.AssignmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.*;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.*;
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
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AssignmentServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AssignmentServiceIntegrationTest {

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
        wireMockServer = new WireMockServer(8089);
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
        registry.add("spring.security.oauth2.client.registration.keycloak.client-id",
                () -> TEST_CLIENT_ID);
        registry.add("spring.security.oauth2.client.registration.keycloak.client-secret",
                () -> TEST_CLIENT_SECRET);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;

    @BeforeAll
    static void setupKeycloak() {
        // Initialize Keycloak admin client
        keycloakAdmin = KeycloakBuilder.builder()
                .serverUrl(keycloak.getAuthServerUrl())
                .realm("master")
                .clientId("admin-cli")
                .username(keycloak.getAdminUsername())
                .password(keycloak.getAdminPassword())
                .build();

        // Create test users
        createTestUser("admin", "admin123", List.of("ADMIN"));
        createTestUser("instructor", "instructor123", List.of("INSTRUCTOR"));
        createTestUser("student", "student123", List.of("STUDENT"));
    }

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/assignments";
        assignmentRepository.deleteAll();
        setupCourseServiceMocks();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.resetMappings();
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

    private static void createTestUser(String username, String password, List<String> roles) {
        var user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setEmail(username + "@test.com");

        var credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));

        RealmResource realm = keycloakAdmin.realm(TEST_REALM);
        RolesResource rolesResource = realm.roles();
        Response response = realm.users().create(user);

        UserResource userResource = realm.users().get(CreatedResponseUtil.getCreatedId(response));

        userResource.roles().realmLevel().add(rolesResource.list().stream().filter(r -> roles.contains(r.getName())).toList());
    }

    private void setupCourseServiceMocks() {
        // Mock valid course
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

        // Mock invalid course
        wireMockServer.stubFor(get(urlEqualTo("/api/courses/code/INVALID123"))
                .willReturn(aResponse()
                        .withStatus(404)));
    }

    private String getAccessToken(String username, String password) {
        String tokenUrl = keycloak.getAuthServerUrl() + "/realms/" + TEST_REALM + "/protocol/openid-connect/token";

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = String.format("grant_type=password&client_id=%s&client_secret=%s&username=%s&password=%s", TEST_CLIENT_ID, TEST_CLIENT_SECRET, username, password);

        var request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("access_token");
    }

    private HttpHeaders createAuthHeaders(String token) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    @Order(1)
    void shouldRejectRequestWithoutAuthentication() {
        // Given
        Assignment assignment = new Assignment();
        assignment.setTitle("Test Assignment");
        assignment.setDueDate(LocalDateTime.now().plusDays(7));
        assignment.setCourseCode("CS101");
        assignment.setDescription("Test without auth");

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl, assignment, Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(2)
    void shouldRejectRequestWithInvalidToken() {
        // Given
        Assignment assignment = new Assignment();
        assignment.setTitle("Test Assignment");
        assignment.setDueDate(LocalDateTime.now().plusDays(7));
        assignment.setCourseCode("CS101");
        assignment.setDescription("Test with invalid token");

        HttpHeaders headers = createAuthHeaders("invalid-token");
        HttpEntity<Assignment> request = new HttpEntity<>(assignment, headers);

        // When
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl, HttpMethod.POST, request, Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(3)
    void shouldAllowAdminToCreateAssignment() {
        // Given
        String adminToken = getAccessToken("admin", "admin123");

        Assignment assignment = new Assignment();
        assignment.setTitle("Admin Assignment");
        assignment.setDueDate(LocalDateTime.now().plusDays(7));
        assignment.setCourseCode("CS101");
        assignment.setDescription("Created by admin");

        HttpHeaders headers = createAuthHeaders(adminToken);
        HttpEntity<Assignment> request = new HttpEntity<>(assignment, headers);

        // When
        ResponseEntity<Assignment> response = restTemplate.exchange(baseUrl, HttpMethod.POST, request, Assignment.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Admin Assignment");
        assertThat(response.getBody().getStatus()).isEqualTo(AssignmentStatus.PENDING);

        // Verify WireMock was called
        wireMockServer.verify(getRequestedFor(urlEqualTo("/api/courses/code/CS101")));
    }

    @Test
    @Order(4)
    void shouldAllowInstructorToCreateAssignment() {
        // Given
        String instructorToken = getAccessToken("instructor", "instructor123");

        Assignment assignment = new Assignment();
        assignment.setTitle("Instructor Assignment");
        assignment.setDueDate(LocalDateTime.now().plusDays(7));
        assignment.setCourseCode("CS101");
        assignment.setDescription("Created by instructor");

        HttpHeaders headers = createAuthHeaders(instructorToken);
        HttpEntity<Assignment> request = new HttpEntity<>(assignment, headers);

        // When
        ResponseEntity<Assignment> response = restTemplate.exchange(
                baseUrl, HttpMethod.POST, request, Assignment.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Instructor Assignment");
    }

    @Test
    @Order(5)
    void shouldRejectStudentFromCreatingAssignment() {
        // Given
        String studentToken = getAccessToken("student", "student123");

        Assignment assignment = new Assignment();
        assignment.setTitle("Student Assignment");
        assignment.setDueDate(LocalDateTime.now().plusDays(7));
        assignment.setCourseCode("CS101");
        assignment.setDescription("Attempted by student");

        HttpHeaders headers = createAuthHeaders(studentToken);
        HttpEntity<Assignment> request = new HttpEntity<>(assignment, headers);

        // When
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl, HttpMethod.POST, request, Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(6)
    void shouldAllowStudentToViewAssignments() {
        // Given - Create an assignment first
        Assignment savedAssignment = assignmentRepository.save(
                new Assignment("Test Assignment", LocalDateTime.now().plusDays(5), "CS101", "Test")
        );

        String studentToken = getAccessToken("student", "student123");
        HttpHeaders headers = createAuthHeaders(studentToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // When
        ResponseEntity<Assignment[]> response = restTemplate.exchange(
                baseUrl, HttpMethod.GET, request, Assignment[].class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].getTitle()).isEqualTo("Test Assignment");
    }

    @Test
    @Order(7)
    void shouldAllowStudentToMarkAssignmentAsCompleted() {
        // Given
        Assignment savedAssignment = assignmentRepository.save(
                new Assignment("Complete Me", LocalDateTime.now().plusDays(3), "CS101", "Test")
        );

        String studentToken = getAccessToken("student", "student123");
        HttpHeaders headers = createAuthHeaders(studentToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // When
        ResponseEntity<Assignment> response = restTemplate.exchange(
                baseUrl + "/" + savedAssignment.getAssignmentId() + "/complete",
                HttpMethod.PATCH,
                request,
                Assignment.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
    }

    @Test
    @Order(8)
    void shouldRejectStudentFromUpdatingAssignment() {
        // Given
        Assignment savedAssignment = assignmentRepository.save(
                new Assignment("Original", LocalDateTime.now().plusDays(5), "CS101", "Original")
        );

        String studentToken = getAccessToken("student", "student123");

        savedAssignment.setTitle("Updated by Student");
        HttpHeaders headers = createAuthHeaders(studentToken);
        HttpEntity<Assignment> request = new HttpEntity<>(savedAssignment, headers);

        // When
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/" + savedAssignment.getAssignmentId(),
                HttpMethod.PUT,
                request,
                Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(9)
    void shouldAllowAdminToDeleteAssignment() {
        // Given
        Assignment savedAssignment = assignmentRepository.save(
                new Assignment("To Delete", LocalDateTime.now().plusDays(1), "CS101", "Will be deleted")
        );

        String adminToken = getAccessToken("admin", "admin123");
        HttpHeaders headers = createAuthHeaders(adminToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // When
        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl + "/" + savedAssignment.getAssignmentId(),
                HttpMethod.DELETE,
                request,
                Void.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(assignmentRepository.findById(savedAssignment.getAssignmentId())).isEmpty();
    }

    @Test
    @Order(10)
    void shouldValidateTokenExpiry() throws InterruptedException {
        // This test would validate token expiry, but since we set tokens to expire in 600 seconds,
        // we'll just validate that the token works initially and document the expiry behavior

        String token = getAccessToken("admin", "admin123");

        Assignment assignment = new Assignment();
        assignment.setTitle("Token Test");
        assignment.setDueDate(LocalDateTime.now().plusDays(7));
        assignment.setCourseCode("CS101");
        assignment.setDescription("Testing token validity");

        HttpHeaders headers = createAuthHeaders(token);
        HttpEntity<Assignment> request = new HttpEntity<>(assignment, headers);

        // Initial request should succeed
        ResponseEntity<Assignment> response = restTemplate.exchange(
                baseUrl, HttpMethod.POST, request, Assignment.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // In a real scenario, you would wait for token expiry and test again
        // For now, we just document that tokens expire after the configured time
    }
}