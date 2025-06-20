//package com.academicplanner.apigateway;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.github.tomakehurst.wiremock.WireMockServer;
//import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
//import dasniko.testcontainers.keycloak.KeycloakContainer;
//import jakarta.ws.rs.core.Response;
//import org.junit.jupiter.api.*;
//import org.keycloak.admin.client.CreatedResponseUtil;
//import org.keycloak.admin.client.Keycloak;
//import org.keycloak.admin.client.KeycloakBuilder;
//import org.keycloak.admin.client.resource.RealmResource;
//import org.keycloak.admin.client.resource.RolesResource;
//import org.keycloak.admin.client.resource.UserResource;
//import org.keycloak.representations.idm.CredentialRepresentation;
//import org.keycloak.representations.idm.UserRepresentation;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.web.client.TestRestTemplate;
//import org.springframework.boot.test.web.server.LocalServerPort;
//import org.springframework.http.*;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//
//import java.util.List;
//import java.util.Map;
//
//import static com.github.tomakehurst.wiremock.client.WireMock.*;
//import static org.assertj.core.api.Assertions.assertThat;
//
//@SpringBootTest(
//    classes = ApiGatewayApplication.class,
//    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
//    properties = {
//        "spring.cloud.gateway.discovery.locator.enabled=false",
//        "eureka.client.enabled=false"
//    }
//)
//@Testcontainers
//@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
//class ApiGatewayIntegrationTest {
//
//    @Container
//    static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:23.0.0")
//            .withRealmImportFile("keycloak/test-realm.json");
//
//    private static WireMockServer courseServiceMock;
//    private static WireMockServer assignmentServiceMock;
//    private static WireMockServer resourceServiceMock;
//    private static WireMockServer eurekaServerMock;
//
//    private static final String TEST_REALM = "test-realm";
//    private static final String GATEWAY_CLIENT_ID = "api-gateway";
//    private static final String GATEWAY_CLIENT_SECRET = "gateway-secret-key";
//
//    @DynamicPropertySource
//    static void configureProperties(DynamicPropertyRegistry registry) {
//        // Keycloak configuration
//        String keycloakUrl = keycloak.getAuthServerUrl();
//        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
//            () -> keycloakUrl + "/realms/" + TEST_REALM + "/protocol/openid-connect/certs");
//        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
//            () -> keycloakUrl + "/realms/" + TEST_REALM);
//
//        // Service URLs for gateway routing (using static routes instead of service discovery)
//        registry.add("spring.cloud.gateway.routes[0].id", () -> "course-service");
//        registry.add("spring.cloud.gateway.routes[0].uri", () -> "http://localhost:8081");
//        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/api/courses/**");
//
//        registry.add("spring.cloud.gateway.routes[1].id", () -> "assignment-service");
//        registry.add("spring.cloud.gateway.routes[1].uri", () -> "http://localhost:8082");
//        registry.add("spring.cloud.gateway.routes[1].predicates[0]", () -> "Path=/api/assignments/**");
//
//        registry.add("spring.cloud.gateway.routes[2].id", () -> "resource-service");
//        registry.add("spring.cloud.gateway.routes[2].uri", () -> "http://localhost:8083");
//        registry.add("spring.cloud.gateway.routes[2].predicates[0]", () -> "Path=/api/resources/**");
//
//        // Disable Eureka for testing
//        registry.add("eureka.client.enabled", () -> "false");
//    }
//
//    @LocalServerPort
//    private int port;
//
//    @Autowired
//    private TestRestTemplate restTemplate;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    private String gatewayUrl;
//
//    private static Keycloak keycloakAdmin;
//
//    @BeforeAll
//    static void setupServices() {
//        // Initialize Keycloak admin client
//        keycloakAdmin = KeycloakBuilder.builder()
//                .serverUrl(keycloak.getAuthServerUrl())
//                .realm("master")
//                .clientId("admin-cli")
//                .username(keycloak.getAdminUsername())
//                .password(keycloak.getAdminPassword())
//                .build();
//
//        // Mock services setup
//        courseServiceMock = new WireMockServer(WireMockConfiguration.options().stubCorsEnabled(false).port(8081));
//        assignmentServiceMock = new WireMockServer(WireMockConfiguration.options().stubCorsEnabled(false).port(8082));
//        resourceServiceMock = new WireMockServer(WireMockConfiguration.options().port(8083));
//        eurekaServerMock = new WireMockServer(WireMockConfiguration.options().port(8761));
//
//        courseServiceMock.start();
//        assignmentServiceMock.start();
//        resourceServiceMock.start();
//        eurekaServerMock.start();
//
//        // Setup mock responses for services
//        setupCourseServiceMocks();
//        setupAssignmentServiceMocks();
//        setupResourceServiceMocks();
//        setupEurekaServerMocks();
//
//        // Create test users
//        createTestUser("admin", "admin123", List.of("ADMIN"));
//        createTestUser("instructor", "instructor123", List.of("INSTRUCTOR"));
//        createTestUser("student", "student123", List.of("STUDENT"));
//    }
//
//    @BeforeEach
//    void setUp() {
//        gatewayUrl = "http://localhost:" + port;
//    }
//
//    @AfterAll
//    static void cleanup() {
//        if (courseServiceMock != null) courseServiceMock.stop();
//        if (assignmentServiceMock != null) assignmentServiceMock.stop();
//        if (resourceServiceMock != null) resourceServiceMock.stop();
//        if (eurekaServerMock != null) eurekaServerMock.stop();
//    }
//
//
//    private static void createTestUser(String username, String password, List<String> roles) {
//        var user = new UserRepresentation();
//        user.setUsername(username);
//        user.setEnabled(true);
//        user.setEmailVerified(true);
//        user.setEmail(username + "@test.com");
//
//        var credential = new CredentialRepresentation();
//        credential.setType(CredentialRepresentation.PASSWORD);
//        credential.setValue(password);
//        credential.setTemporary(false);
//        user.setCredentials(List.of(credential));
//
//        RealmResource realm = keycloakAdmin.realm(TEST_REALM);
//        RolesResource rolesResource = realm.roles();
//        Response response = realm.users().create(user);
//
//        UserResource userResource = realm.users().get(CreatedResponseUtil.getCreatedId(response));
//
//        userResource.roles().realmLevel().add(rolesResource.list().stream().filter(r -> roles.contains(r.getName())).toList());
//    }
//
//    private static void setupCourseServiceMocks() {
//        // Mock course list endpoint
//        courseServiceMock.stubFor(get(urlEqualTo("/api/courses"))
//                .willReturn(aResponse()
//                        .withStatus(200)
//                        .withHeader("Content-Type", "application/json")
//                        .withBody("""
//                            [
//                                {
//                                    "id": 1,
//                                    "courseCode": "CS101",
//                                    "title": "Introduction to Programming",
//                                    "department": "Computer Science",
//                                    "credits": 3
//                                },
//                                {
//                                    "id": 2,
//                                    "courseCode": "MATH101",
//                                    "title": "Calculus I",
//                                    "department": "Mathematics",
//                                    "credits": 4
//                                }
//                            ]
//                            """)));
//
//        // Mock public endpoint
//        courseServiceMock.stubFor(get(urlEqualTo("/api/courses/public"))
//                .willReturn(aResponse()
//                        .withStatus(200)
//                        .withHeader("Content-Type", "application/json")
//                        .withBody("[{\"courseCode\":\"CS101\",\"title\":\"Public Course\"}]")));
//
//        // Mock create course endpoint
//        courseServiceMock.stubFor(post(urlEqualTo("/api/courses"))
//                .willReturn(aResponse()
//                        .withStatus(201)
//                        .withHeader("Content-Type", "application/json")
//                        .withBody("{\"id\":3,\"courseCode\":\"CS201\",\"title\":\"Data Structures\"}")));
//    }
//
//    private static void setupAssignmentServiceMocks() {
//        // Mock assignment list endpoint
//        assignmentServiceMock.stubFor(get(urlEqualTo("/api/assignments"))
//                .willReturn(aResponse()
//                        .withStatus(200)
//                        .withHeader("Content-Type", "application/json")
//                        .withBody("""
//                            [
//                                {
//                                    "assignmentId": "1",
//                                    "title": "Homework 1",
//                                    "courseCode": "CS101",
//                                    "status": "PENDING"
//                                }
//                            ]
//                            """)));
//
//        // Mock create assignment endpoint
//        assignmentServiceMock.stubFor(post(urlEqualTo("/api/assignments"))
//                .willReturn(aResponse()
//                        .withStatus(201)
//                        .withHeader("Content-Type", "application/json")
//                        .withBody("{\"assignmentId\":\"2\",\"title\":\"New Assignment\"}")));
//    }
//
//    private static void setupResourceServiceMocks() {
//        // Mock resource list endpoint
//        resourceServiceMock.stubFor(get(urlEqualTo("/api/resources"))
//                .willReturn(aResponse()
//                        .withStatus(200)
//                        .withHeader("Content-Type", "application/json")
//                        .withBody("""
//                            [
//                                {
//                                    "id": 1,
//                                    "title": "Library",
//                                    "url": "https://library.edu",
//                                    "category": "LIBRARY"
//                                }
//                            ]
//                            """)));
//
//        // Mock public endpoint
//        resourceServiceMock.stubFor(get(urlEqualTo("/api/resources/public"))
//                .willReturn(aResponse()
//                        .withStatus(200)
//                        .withHeader("Content-Type", "application/json")
//                        .withBody("[{\"title\":\"Public Resource\"}]")));
//    }
//
//    private static void setupEurekaServerMocks() {
//        // Mock Eureka apps endpoint
//        eurekaServerMock.stubFor(get(urlEqualTo("/eureka/apps"))
//                .willReturn(aResponse()
//                        .withStatus(200)
//                        .withHeader("Content-Type", "application/json")
//                        .withBody("{\"applications\":{\"application\":[]}}")));
//    }
//
//    private String getAccessToken(String username, String password) {
//        String tokenUrl = keycloak.getAuthServerUrl() + "/realms/" + TEST_REALM + "/protocol/openid-connect/token";
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
//
//        String body = String.format("grant_type=password&client_id=%s&client_secret=%s&username=%s&password=%s",
//                GATEWAY_CLIENT_ID, GATEWAY_CLIENT_SECRET, username, password);
//
//        HttpEntity<String> request = new HttpEntity<>(body, headers);
//
//        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
//
//        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
//        return (String) response.getBody().get("access_token");
//    }
//
//    private HttpHeaders createAuthHeaders(String token) {
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//        headers.setBearerAuth(token);
//        return headers;
//    }
//
//    @Test
//    @Order(1)
//    void shouldRejectUnauthenticatedRequestsToProtectedEndpoints() {
//        // When - Request without authentication
//        ResponseEntity<String> response = restTemplate.getForEntity(
//                gatewayUrl + "/api/courses", String.class);
//
//        // Then
//        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
//    }
//
//    @Test
//    @Order(2)
//    void shouldAllowAccessToPublicEndpoints() {
//        // When - Request to public endpoint without authentication
//        ResponseEntity<String> coursesResponse = restTemplate.getForEntity(
//                gatewayUrl + "/api/courses/public", String.class);
//
//        ResponseEntity<String> resourcesResponse = restTemplate.getForEntity(
//                gatewayUrl + "/api/resources/public", String.class);
//
//        // Then
//        assertThat(coursesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
//        assertThat(coursesResponse.getBody()).contains("Public Course");
//
//        assertThat(resourcesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
//        assertThat(resourcesResponse.getBody()).contains("Public Resource");
//    }
//
//    @Test
//    @Order(3)
//    void shouldRouteAuthenticatedRequestsToCourseService() {
//        // Given
//        String studentToken = getAccessToken("student", "student123");
//        HttpHeaders headers = createAuthHeaders(studentToken);
//        HttpEntity<Void> request = new HttpEntity<>(headers);
//
//        // When
//        ResponseEntity<String> response = restTemplate.exchange(
//                gatewayUrl + "/api/courses",
//                HttpMethod.GET,
//                request,
//                String.class);
//
//        // Then
//        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
//        assertThat(response.getBody()).contains("CS101");
//        assertThat(response.getBody()).contains("Introduction to Programming");
//
//        // Verify the request was routed to course service
//        courseServiceMock.verify(getRequestedFor(urlEqualTo("/api/courses")));
//    }
//
//    @Test
//    @Order(4)
//    void shouldRouteAuthenticatedRequestsToAssignmentService() {
//        // Given
//        String studentToken = getAccessToken("student", "student123");
//        HttpHeaders headers = createAuthHeaders(studentToken);
//        HttpEntity<Void> request = new HttpEntity<>(headers);
//
//        // When
//        ResponseEntity<String> response = restTemplate.exchange(
//                gatewayUrl + "/api/assignments",
//                HttpMethod.GET,
//                request,
//                String.class);
//
//        // Then
//        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
//        assertThat(response.getBody()).contains("Homework 1");
//
//        // Verify the request was routed to assignment service
//        assignmentServiceMock.verify(getRequestedFor(urlEqualTo("/api/assignments")));
//    }
//
//    @Test
//    @Order(5)
//    void shouldRouteAuthenticatedRequestsToResourceService() {
//        // Given
//        String studentToken = getAccessToken("student", "student123");
//        HttpHeaders headers = createAuthHeaders(studentToken);
//        HttpEntity<Void> request = new HttpEntity<>(headers);
//
//        // When
//        ResponseEntity<String> response = restTemplate.exchange(
//                gatewayUrl + "/api/resources",
//                HttpMethod.GET,
//                request,
//                String.class);
//
//        // Then
//        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
//        assertThat(response.getBody()).contains("Library");
//
//        // Verify the request was routed to resource service
//        resourceServiceMock.verify(getRequestedFor(urlEqualTo("/api/resources")));
//    }
//
//    @Test
//    @Order(6)
//    void shouldEnforceRoleBasedAccessControl() {
//        // Given
//        String adminToken = getAccessToken("admin", "admin123");
//        String instructorToken = getAccessToken("instructor", "instructor123");
//        String studentToken = getAccessToken("student", "student123");
//
//        Map<String, Object> courseData = Map.of(
//            "courseCode", "CS301",
//            "title", "Advanced Programming",
//            "department", "Computer Science",
//            "credits", 4
//        );
//
//        // Test 1: Admin should be able to create course
//        HttpHeaders adminHeaders = createAuthHeaders(adminToken);
//        HttpEntity<Map<String, Object>> adminRequest = new HttpEntity<>(courseData, adminHeaders);
//
//        ResponseEntity<String> adminResponse = restTemplate.exchange(
//                gatewayUrl + "/api/courses",
//                HttpMethod.POST,
//                adminRequest,
//                String.class);
//
//        assertThat(adminResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
//
//        // Test 2: Instructor should be forbidden from creating course
//        HttpHeaders instructorHeaders = createAuthHeaders(instructorToken);
//        HttpEntity<Map<String, Object>> instructorRequest = new HttpEntity<>(courseData, instructorHeaders);
//
//        ResponseEntity<String> instructorResponse = restTemplate.exchange(
//                gatewayUrl + "/api/courses",
//                HttpMethod.POST,
//                instructorRequest,
//                String.class);
//
//        assertThat(instructorResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
//
//        // Test 3: Student should be forbidden from creating course
//        HttpHeaders studentHeaders = createAuthHeaders(studentToken);
//        HttpEntity<Map<String, Object>> studentRequest = new HttpEntity<>(courseData, studentHeaders);
//
//        ResponseEntity<String> studentResponse = restTemplate.exchange(
//                gatewayUrl + "/api/courses",
//                HttpMethod.POST,
//                studentRequest,
//                String.class);
//
//        assertThat(studentResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
//    }
//
//    @Test
//    @Order(7)
//    void shouldHandleCorsRequests() {
//        // Given
//        HttpHeaders headers = new HttpHeaders();
//        headers.setOrigin("http://localhost:3000");
//        headers.setAccessControlRequestMethod(HttpMethod.GET);
//        headers.setAccessControlRequestHeaders(List.of("Authorization", "Content-Type"));
//
//        HttpEntity<Void> request = new HttpEntity<>(headers);
//
//        // When - Preflight request
//        ResponseEntity<String> response = restTemplate.exchange(
//                gatewayUrl + "/api/courses",
//                HttpMethod.OPTIONS,
//                request,
//                String.class);
//
//        // Then
//        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
//        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("*");
//        assertThat(response.getHeaders().getAccessControlAllowMethods())
//                .contains(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE);
//    }
//
//    @Test
//    @Order(8)
//    void shouldAddGatewayHeadersToRequests() {
//        // Given
//        String adminToken = getAccessToken("admin", "admin123");
//        HttpHeaders headers = createAuthHeaders(adminToken);
//        HttpEntity<Void> request = new HttpEntity<>(headers);
//
//        // When
//        ResponseEntity<String> response = restTemplate.exchange(
//                gatewayUrl + "/api/courses",
//                HttpMethod.GET,
//                request,
//                String.class);
//
//        // Then
//        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
//        assertThat(response.getHeaders().get("X-Gateway-Response")).contains("course-service");
//
//        // Verify gateway added headers to backend request
//        courseServiceMock.verify(getRequestedFor(urlEqualTo("/api/courses"))
//                .withHeader("X-Gateway-Request", equalTo("true")));
//    }
//
//    @Test
//    @Order(9)
//    void shouldHandleInvalidTokenGracefully() {
//        // Given
//        HttpHeaders headers = createAuthHeaders("invalid.jwt.token");
//        HttpEntity<Void> request = new HttpEntity<>(headers);
//
//        // When
//        ResponseEntity<String> response = restTemplate.exchange(
//                gatewayUrl + "/api/courses",
//                HttpMethod.GET,
//                request,
//                String.class);
//
//        // Then
//        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
//    }
//
//    @Test
//    @Order(10)
//    void shouldAccessGatewayInfoEndpoints() {
//        // Test gateway info endpoint
//        ResponseEntity<Map> infoResponse = restTemplate.getForEntity(
//                gatewayUrl + "/gateway/info", Map.class);
//
//        assertThat(infoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
//        assertThat(infoResponse.getBody()).containsKey("name");
//        assertThat(infoResponse.getBody().get("name")).isEqualTo("Academic Planner API Gateway");
//
//        // Test gateway health endpoint
//        ResponseEntity<Map> healthResponse = restTemplate.getForEntity(
//                gatewayUrl + "/gateway/health", Map.class);
//
//        assertThat(healthResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
//        assertThat(healthResponse.getBody()).containsEntry("status", "UP");
//    }
//
//    @Test
//    @Order(11)
//    void shouldRouteSwaggerRequestsWithAuthentication() {
//        // Given - Only admin should access swagger
//        String adminToken = getAccessToken("admin", "admin123");
//        String studentToken = getAccessToken("student", "student123");
//
//        // Test 1: Admin can access swagger services
//        HttpHeaders adminHeaders = createAuthHeaders(adminToken);
//        HttpEntity<Void> adminRequest = new HttpEntity<>(adminHeaders);
//
//        ResponseEntity<Map> adminResponse = restTemplate.exchange(
//                gatewayUrl + "/swagger/services",
//                HttpMethod.GET,
//                adminRequest,
//                Map.class);
//
//        assertThat(adminResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
//        assertThat(adminResponse.getBody()).containsKey("course-service");
//        assertThat(adminResponse.getBody()).containsKey("assignment-service");
//        assertThat(adminResponse.getBody()).containsKey("resource-service");
//
//        // Test 2: Student cannot access swagger
//        HttpHeaders studentHeaders = createAuthHeaders(studentToken);
//        HttpEntity<Void> studentRequest = new HttpEntity<>(studentHeaders);
//
//        ResponseEntity<String> studentResponse = restTemplate.exchange(
//                gatewayUrl + "/swagger/services",
//                HttpMethod.GET,
//                studentRequest,
//                String.class);
//
//        assertThat(studentResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
//    }
//
//    @Test
//    @Order(12)
//    void shouldHandleServiceUnavailable() {
//        // Given - Stop a mock service
//        courseServiceMock.stop();
//
//        String studentToken = getAccessToken("student", "student123");
//        HttpHeaders headers = createAuthHeaders(studentToken);
//        HttpEntity<Void> request = new HttpEntity<>(headers);
//
//        // When
//        ResponseEntity<String> response = restTemplate.exchange(
//                gatewayUrl + "/api/courses",
//                HttpMethod.GET,
//                request,
//                String.class);
//
//        // Then
//        assertThat(response.getStatusCode()).isIn(
//            HttpStatus.SERVICE_UNAVAILABLE,
//            HttpStatus.GATEWAY_TIMEOUT,
//            HttpStatus.INTERNAL_SERVER_ERROR
//        );
//
//        // Restart for other tests
//        courseServiceMock.start();
//        setupCourseServiceMocks();
//    }
//}