package com.college.academicplanner;

import com.academicplanner.courseservice.CourseServiceApplication;
import com.academicplanner.courseservice.entity.Course;
import com.academicplanner.courseservice.repository.CourseRepository;
import com.academicplanner.courseservice.service.CourseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.*;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CourseServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CourseServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.1")
            .withDatabaseName("course_test_db")
            .withUsername("test_user")
            .withPassword("test_password");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:23.0.0")
            .withRealmImportFile("keycloak/test-realm.json");

    private static Keycloak keycloakAdmin;
    private static final String TEST_REALM = "test-realm";
    private static final String TEST_CLIENT_ID = "course-service";
    private static final String TEST_CLIENT_SECRET = "course-service-secret";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL configuration
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis configuration
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        // JPA configuration
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

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
    private CourseRepository courseRepository;

    @Autowired
    private CourseService courseService;

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

        // Create test realm and users
        createTestRealm();
    }

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/courses";
        courseRepository.deleteAll();
    }

    @AfterAll
    static void cleanup() {
        if (keycloakAdmin != null) {
            keycloakAdmin.close();
        }
    }

    private static void createTestRealm() {
//        // Create realm
//        RealmRepresentation realm = new RealmRepresentation();
//        realm.setRealm(TEST_REALM);
//        realm.setEnabled(true);
//        realm.setAccessTokenLifespan(600);
//
//        // Create client
//        ClientRepresentation client = new ClientRepresentation();
//        client.setClientId(TEST_CLIENT_ID);
//        client.setSecret(TEST_CLIENT_SECRET);
//        client.setServiceAccountsEnabled(true);
//        client.setDirectAccessGrantsEnabled(true);
//        client.setPublicClient(false);
//        client.setProtocol("openid-connect");
//        realm.setClients(Arrays.asList(client));
//
//        // Create roles
//        realm.setRoles(Map.of(
//            "realm", Arrays.asList(
//                Map.of("name", "ADMIN", "description", "Admin role"),
//                Map.of("name", "INSTRUCTOR", "description", "Instructor role"),
//                Map.of("name", "STUDENT", "description", "Student role")
//            )
//        ));
//
//        keycloakAdmin.realms().create(realm);

        // Create test users
        createTestUser("admin", "admin123", List.of("ADMIN"));
        createTestUser("instructor", "instructor123", List.of("INSTRUCTOR"));
        createTestUser("student", "student123", List.of("STUDENT"));
    }

    private static void createTestUser(String username, String password, List<String> roles) {
        var user = new UserRepresentation();
        user.setUsername(username);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setEmail(username + "@test.com");
        user.setFirstName("Test");
        user.setLastName(username);

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
    void shouldRejectRequestWithoutAuthentication() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl, String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(2)
    void shouldAllowPublicAccessToPublicEndpoints() {
        // Given - Create some courses
        courseRepository.save(new Course("CS101", "Intro to Programming", "Basic programming", "Computer Science", 3));
        courseRepository.save(new Course("MATH101", "Calculus I", "Differential calculus", "Mathematics", 4));

        // When - Access public endpoint without authentication
        ResponseEntity<Course[]> response = restTemplate.getForEntity(
                baseUrl + "/public", Course[].class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

//    @Test
//    @Order(3)
//    void shouldAllowAuthenticatedUsersToViewCourses() {
//        // Given
//        Course course = courseRepository.save(
//                new Course("CS101", "Programming I", "Basic programming", "Computer Science", 3)
//        );
//
//        String studentToken = getAccessToken("student", "student123");
//        HttpHeaders headers = createAuthHeaders(studentToken);
//        HttpEntity<Void> request = new HttpEntity<>(headers);
//
//        // When
//        ResponseEntity<Course[]> response = restTemplate.exchange(baseUrl, HttpMethod.GET, request, Course[].class);
//
//        // Then
//        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
//        assertThat(response.getBody()).hasSize(1);
//        assertThat(response.getBody()[0].getCourseCode()).isEqualTo("CS101");
//    }

    @Test
    @Order(4)
    void shouldAllowAdminToCreateCourse() {
        // Given
        String adminToken = getAccessToken("admin", "admin123");

        Course course = new Course("CS201", "Data Structures", "Advanced data structures", "Computer Science", 4);

        HttpHeaders headers = createAuthHeaders(adminToken);
        HttpEntity<Course> request = new HttpEntity<>(course, headers);

        // When
        ResponseEntity<Course> response = restTemplate.exchange(
                baseUrl, HttpMethod.POST, request, Course.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCourseCode()).isEqualTo("CS201");
        assertThat(response.getBody().getTitle()).isEqualTo("Data Structures");
    }

    @Test
    @Order(5)
    void shouldRejectInstructorFromCreatingCourse() {
        // Given
        String instructorToken = getAccessToken("instructor", "instructor123");

        Course course = new Course("CS301", "Algorithms", "Algorithm analysis", "Computer Science", 4);

        HttpHeaders headers = createAuthHeaders(instructorToken);
        HttpEntity<Course> request = new HttpEntity<>(course, headers);

        // When
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl, HttpMethod.POST, request, Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(6)
    void shouldRejectStudentFromCreatingCourse() {
        // Given
        String studentToken = getAccessToken("student", "student123");

        Course course = new Course("CS401", "AI", "Artificial Intelligence", "Computer Science", 4);

        HttpHeaders headers = createAuthHeaders(studentToken);
        HttpEntity<Course> request = new HttpEntity<>(course, headers);

        // When
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl, HttpMethod.POST, request, Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(7)
    void shouldAllowAdminToUpdateCourse() {
        // Given
        Course savedCourse = courseRepository.save(
                new Course("UPDATE101", "Original Title", "Original Description", "Computer Science", 3)
        );

        String adminToken = getAccessToken("admin", "admin123");

        savedCourse.setTitle("Updated Title");
        savedCourse.setDescription("Updated Description");

        HttpHeaders headers = createAuthHeaders(adminToken);
        HttpEntity<Course> request = new HttpEntity<>(savedCourse, headers);

        // When
        ResponseEntity<Course> response = restTemplate.exchange(
                baseUrl + "/" + savedCourse.getId(),
                HttpMethod.PUT,
                request,
                Course.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTitle()).isEqualTo("Updated Title");
        assertThat(response.getBody().getDescription()).isEqualTo("Updated Description");
    }

    @Test
    @Order(8)
    void shouldAllowAdminToDeleteCourse() {
        // Given
        Course savedCourse = courseRepository.save(
                new Course("DELETE101", "To Delete", "Will be deleted", "Computer Science", 3)
        );

        String adminToken = getAccessToken("admin", "admin123");
        HttpHeaders headers = createAuthHeaders(adminToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // When
        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl + "/" + savedCourse.getId(),
                HttpMethod.DELETE,
                request,
                Void.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(courseRepository.findById(savedCourse.getId())).isEmpty();
    }

//    @Test
//    @Order(9)
//    void shouldValidateDifferentTokenScopes() {
//        // Given - Create courses with different departments
//        courseRepository.saveAll(List.of(
//                new Course("CS101", "Programming", "Intro to programming", "Computer Science", 3),
//                new Course("MATH101", "Calculus", "Differential calculus", "Mathematics", 4)
//        ));
//
//        // Test with different user roles
//        String adminToken = getAccessToken("admin", "admin123");
//        String instructorToken = getAccessToken("instructor", "instructor123");
//        String studentToken = getAccessToken("student", "student123");
//
//        // All should be able to read courses
//        for (String token : Arrays.asList(adminToken, instructorToken, studentToken)) {
//            HttpHeaders headers = createAuthHeaders(token);
//            HttpEntity<Void> request = new HttpEntity<>(headers);
//
//            ResponseEntity<Course[]> response = restTemplate.exchange(
//                    baseUrl + "/department/Computer Science",
//                    HttpMethod.GET,
//                    request,
//                    Course[].class);
//
//            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
//            assertThat(response.getBody()).hasSize(1);
//        }
//    }

    @Test
    @Order(10)
    void shouldHandleInvalidTokenGracefully() {
        // Given
        HttpHeaders headers = createAuthHeaders("invalid.jwt.token");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // When
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl, HttpMethod.GET, request, Map.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

//    @Test
//    @Order(11)
//    void shouldCacheCoursesAndServeFromCacheWithAuth() {
//        // Given
//        Course course = courseRepository.save(
//                new Course("CACHE101", "Cache Test", "Testing cache", "Computer Science", 3)
//        );
//
//        String studentToken = getAccessToken("student", "student123");
//        HttpHeaders headers = createAuthHeaders(studentToken);
//        HttpEntity<Void> request = new HttpEntity<>(headers);
//
//        // When - First call (should hit database)
//        long startTime1 = System.currentTimeMillis();
//        ResponseEntity<Course> response1 = restTemplate.exchange(
//                baseUrl + "/" + course.getId(),
//                HttpMethod.GET,
//                request,
//                Course.class);
//        long endTime1 = System.currentTimeMillis();
//
//        // When - Second call (should hit cache)
//        long startTime2 = System.currentTimeMillis();
//        ResponseEntity<Course> response2 = restTemplate.exchange(
//                baseUrl + "/" + course.getId(),
//                HttpMethod.GET,
//                request,
//                Course.class);
//        long endTime2 = System.currentTimeMillis();
//
//        // Then
//        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
//        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
//        assertThat(response1.getBody().getCourseCode()).isEqualTo("CACHE101");
//        assertThat(response2.getBody().getCourseCode()).isEqualTo("CACHE101");
//
//        // Log timing for verification
//        System.out.println("First call time: " + (endTime1 - startTime1) + "ms");
//        System.out.println("Second call time: " + (endTime2 - startTime2) + "ms");
//    }

//    @Test
//    @Order(12)
//    void shouldSearchCoursesWithAuthentication() {
//        // Given
//        courseRepository.saveAll(List.of(
//                new Course("CS101", "Java Programming", "Programming in Java", "Computer Science", 3),
//                new Course("CS201", "Python Programming", "Programming in Python", "Computer Science", 4),
//                new Course("MATH101", "Statistics", "Statistical analysis", "Mathematics", 3)
//        ));
//
//        String instructorToken = getAccessToken("instructor", "instructor123");
//        HttpHeaders headers = createAuthHeaders(instructorToken);
//        HttpEntity<Void> request = new HttpEntity<>(headers);
//
//        // When
//        ResponseEntity<Course[]> response = restTemplate.exchange(
//                baseUrl + "/search?keyword=programming",
//                HttpMethod.GET,
//                request,
//                Course[].class);
//
//        // Then
//        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
//        assertThat(response.getBody()).hasSize(2);
//        assertThat(response.getBody()).extracting(Course::getTitle).allMatch(title -> title.toLowerCase().contains("programming"));
//    }
}