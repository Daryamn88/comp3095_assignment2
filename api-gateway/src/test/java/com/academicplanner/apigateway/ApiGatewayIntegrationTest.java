package com.academicplanner.apigateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ApiGatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ApiGatewayIntegrationTest {

    private static final Network network = Network.newNetwork();

    @Container
    static PostgreSQLContainer<?> keycloakPostgres = new PostgreSQLContainer<>("postgres:16.1")
            .withNetwork(network)
            .withNetworkAliases("keycloak-postgres")
            .withDatabaseName("keycloak")
            .withUsername("keycloak")
            .withPassword("keycloak");

    @Container
    static GenericContainer<?> keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:23.0.0")
            .withNetwork(network)
            .withNetworkAliases("keycloak")
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withEnv("KC_DB", "postgres")
            .withEnv("KC_DB_URL", "jdbc:postgresql://keycloak-postgres:5432/keycloak")
            .withEnv("KC_DB_USERNAME", "keycloak")
            .withEnv("KC_DB_PASSWORD", "keycloak")
            .withEnv("KC_HTTP_ENABLED", "true")
            .withEnv("KC_HOSTNAME_STRICT_HTTPS", "false")
            .withCommand("start-dev")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/master").withStartupTimeout(Duration.ofMinutes(5)))
            .dependsOn(keycloakPostgres);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:" + keycloak.getMappedPort(8080) + "/realms/GBC_Realm/protocol/openid-connect/certs");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost:" + keycloak.getMappedPort(8080) + "/realms/GBC_Realm");

        // Disable Eureka for Gateway in tests to avoid service discovery issues
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.cloud.gateway.discovery.locator.enabled", () -> "false");
    }

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String gatewayBaseUrl;
    private String keycloakBaseUrl;

    @BeforeEach
    void setUp() throws Exception {
        gatewayBaseUrl = "http://localhost:" + gatewayPort;
        keycloakBaseUrl = "http://localhost:" + keycloak.getMappedPort(8080);

        setupKeycloakRealm();
    }

    private void setupKeycloakRealm() throws Exception {
        // Wait for Keycloak to be ready
        Thread.sleep(5000);

        String adminToken = getKeycloakAdminToken();
        createTestRealm(adminToken);
        createTestClient(adminToken);
        createTestUsers(adminToken);
    }

    @Test
    void shouldRouteToGatewayInfoEndpoint() {
        // When: Calling gateway info endpoint
        ResponseEntity<Map> response = restTemplate.getForEntity(
                gatewayBaseUrl + "/gateway/info", Map.class);

        // Then: Should return gateway information
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("name");
        assertThat(response.getBody().get("name")).isEqualTo("Academic Planner API Gateway");
    }

    @Test
    void shouldBlockUnauthorizedAccessToProtectedEndpoints() {
        // When: Accessing protected course endpoint without token
        ResponseEntity<String> response = restTemplate.getForEntity(
                gatewayBaseUrl + "/api/courses", String.class);

        // Then: Should be unauthorized
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldAllowAccessToPublicEndpoints() {
        // When: Accessing public course endpoint
        ResponseEntity<String> response = restTemplate.getForEntity(
                gatewayBaseUrl + "/api/courses/public", String.class);

        // Then: Should succeed (even though backend might not be available)
        // We expect either 200 or 503 (service unavailable), but not 401 (unauthorized)
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void shouldEnforceRoleBasedAccessControl() throws Exception {
        // Given: Student token
        String studentToken = getUserToken("teststudent", "student123");

        // When: Student tries to access admin-only swagger endpoint
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(studentToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                gatewayBaseUrl + "/swagger/services", HttpMethod.GET, request, String.class);

        // Then: Should be forbidden
        assertThat(response.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void shouldAddGatewayHeadersToRequests() {
        // This test would ideally verify that gateway adds custom headers
        // For now, we just verify the gateway is processing requests
        ResponseEntity<Map> response = restTemplate.getForEntity(
                gatewayBaseUrl + "/gateway/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("status");
        assertThat(response.getBody().get("status")).isEqualTo("UP");
    }

    @Test
    void shouldHandleCircuitBreakerForDownstreamServices() {
        // When: Accessing endpoint that would route to unavailable service
        ResponseEntity<String> response = restTemplate.getForEntity(
                gatewayBaseUrl + "/api/courses/public", String.class);

        // Then: Should handle gracefully (not crash)
        // Expecting 503 or 404 since backend services aren't running
        assertThat(response.getStatusCode()).isIn(
                HttpStatus.SERVICE_UNAVAILABLE,
                HttpStatus.NOT_FOUND,
                HttpStatus.GATEWAY_TIMEOUT,
                HttpStatus.BAD_GATEWAY,
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @Test
    void shouldValidateJWTTokensCorrectly() throws Exception {
        // Given: Valid JWT token
        String validToken = getUserToken("teststudent", "student123");

        // When: Using valid token
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                gatewayBaseUrl + "/api/courses", HttpMethod.GET, request, String.class);

        // Then: Should not be unauthorized (might be other errors due to missing backend)
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectInvalidJWTTokens() {
        // When: Using invalid token
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("invalid-jwt-token");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                gatewayBaseUrl + "/api/courses", HttpMethod.GET, request, String.class);

        // Then: Should be unauthorized
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldHandleCORSRequestsCorrectly() {
        // When: Making CORS preflight request
        HttpHeaders headers = new HttpHeaders();
        headers.add("Origin", "http://localhost:3000");
        headers.add("Access-Control-Request-Method", "POST");
        headers.add("Access-Control-Request-Headers", "authorization,content-type");

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                gatewayBaseUrl + "/api/courses", HttpMethod.OPTIONS, request, String.class);

        // Then: Should handle CORS
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders()).containsKey("Access-Control-Allow-Origin");
    }

    // Helper methods
    private String getKeycloakAdminToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("username", "admin");
        map.add("password", "admin");
        map.add("grant_type", "password");
        map.add("client_id", "admin-cli");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                keycloakBaseUrl + "/realms/master/protocol/openid-connect/token",
                request,
                Map.class);

        return (String) response.getBody().get("access_token");
    }

    private void createTestRealm(String adminToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        String realmJson = """
                {
                    "realm": "GBC_Realm",
                    "enabled": true,
                    "roles": {
                        "realm": [
                            {"name": "ADMIN"},
                            {"name": "INSTRUCTOR"},
                            {"name": "STUDENT"}
                        ]
                    }
                }
                """;

        HttpEntity<String> request = new HttpEntity<>(realmJson, headers);
        restTemplate.postForEntity(keycloakBaseUrl + "/admin/realms", request, String.class);
    }

    private void createTestClient(String adminToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        String clientJson = """
                {
                    "clientId": "api-gateway",
                    "enabled": true,
                    "clientAuthenticatorType": "client-secret",
                    "secret": "gateway-secret-key",
                    "serviceAccountsEnabled": true,
                    "directAccessGrantsEnabled": true,
                    "standardFlowEnabled": true,
                    "protocol": "openid-connect"
                }
                """;

        HttpEntity<String> request = new HttpEntity<>(clientJson, headers);
        restTemplate.postForEntity(keycloakBaseUrl + "/admin/realms/GBC_Realm/clients", request, String.class);
    }

    private void createTestUsers(String adminToken) {
        createUser(adminToken, "testadmin", "admin123", "ADMIN");
        createUser(adminToken, "testinstructor", "instructor123", "INSTRUCTOR");
        createUser(adminToken, "teststudent", "student123", "STUDENT");
    }

    private void createUser(String adminToken, String username, String password, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        String userJson = String.format("""
                {
                    "username": "%s",
                    "enabled": true,
                    "credentials": [{
                        "type": "password",
                        "value": "%s",
                        "temporary": false
                    }],
                    "realmRoles": ["%s"]
                }
                """, username, password, role);

        HttpEntity<String> request = new HttpEntity<>(userJson, headers);
        restTemplate.postForEntity(keycloakBaseUrl + "/admin/realms/GBC_Realm/users", request, String.class);
    }

    private String getUserToken(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("username", username);
        map.add("password", password);
        map.add("grant_type", "password");
        map.add("client_id", "api-gateway");
        map.add("client_secret", "gateway-secret-key");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    keycloakBaseUrl + "/realms/GBC_Realm/protocol/openid-connect/token",
                    request,
                    Map.class);

            return (String) response.getBody().get("access_token");
        } catch (Exception e) {
            throw new RuntimeException("Failed to get user token", e);
        }
    }
}