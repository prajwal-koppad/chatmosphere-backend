package com.chatmosphere.backend;

import com.chatmosphere.backend.dto.RoomDTO;
import com.chatmosphere.backend.vo.AuthResponseVO;
import com.chatmosphere.backend.vo.CreateRoomRequestVO;
import com.chatmosphere.backend.vo.LoginRequestVO;
import com.chatmosphere.backend.vo.SignupRequestVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnabledIf("isDockerAvailable")
public class RoomControllerIntegrationTest {

    static boolean isDockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("chatmosphere")
            .withUsername("root")
            .withPassword("admin@123");

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:6.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void testAuthAndRoomCreationFlow() {
        // 1. Signup a user
        SignupRequestVO signup = new SignupRequestVO();
        signup.setUsername("testuser");
        signup.setPassword("password123");
        signup.setDisplayName("Test User");
        signup.setAvatarUrl("avatar.png");

        ResponseEntity<AuthResponseVO> signupResponse = restTemplate.postForEntity(
                "/api/v1/auth/signup", signup, AuthResponseVO.class);
        assertEquals(HttpStatus.CREATED, signupResponse.getStatusCode());
        assertNotNull(signupResponse.getBody());
        assertNotNull(signupResponse.getBody().getToken());

        // 2. Login the user
        LoginRequestVO login = new LoginRequestVO();
        login.setUsername("testuser");
        login.setPassword("password123");

        ResponseEntity<AuthResponseVO> loginResponse = restTemplate.postForEntity(
                "/api/v1/auth/login", login, AuthResponseVO.class);
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        String jwtToken = loginResponse.getBody().getToken();

        // 3. Create a room (Authenticated)
        CreateRoomRequestVO createRoom = new CreateRoomRequestVO();
        createRoom.setRoomId("gaming-lounge");
        createRoom.setRoomName("Gaming Lounge");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);
        HttpEntity<CreateRoomRequestVO> createRequest = new HttpEntity<>(createRoom, headers);

        ResponseEntity<RoomDTO> roomResponse = restTemplate.postForEntity(
                "/api/v1/rooms/create-room", createRequest, RoomDTO.class);
        assertEquals(HttpStatus.CREATED, roomResponse.getStatusCode());
        assertNotNull(roomResponse.getBody());
        assertEquals("gaming-lounge", roomResponse.getBody().getRoomId());

        // 4. Retrieve Room details (Authenticated)
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);
        ResponseEntity<RoomDTO> getRoomResponse = restTemplate.exchange(
                "/api/v1/rooms/gaming-lounge", HttpMethod.GET, getRequest, RoomDTO.class);
        assertEquals(HttpStatus.OK, getRoomResponse.getStatusCode());
        assertEquals("Gaming Lounge", getRoomResponse.getBody().getRoomName());
    }
}
