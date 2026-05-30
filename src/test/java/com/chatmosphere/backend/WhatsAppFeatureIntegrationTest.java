package com.chatmosphere.backend;

import com.chatmosphere.backend.dto.*;
import com.chatmosphere.backend.documents.EmailLog;
import com.chatmosphere.backend.documents.Message;
import com.chatmosphere.backend.entity.User;
import com.chatmosphere.backend.repository.*;
import com.chatmosphere.backend.vo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnabledIf("isDockerAvailable")
public class WhatsAppFeatureIntegrationTest {

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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailLogRepository emailLogRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    public void testWhatsAppStyleFeatures() {
        // --- 1. SIGNUP TWO USERS ---
        SignupRequestVO aliceSignup = new SignupRequestVO();
        aliceSignup.setUsername("alice");
        aliceSignup.setPassword("password123");
        aliceSignup.setDisplayName("Alice In Wonderland");
        aliceSignup.setAvatarUrl("alice.png");
        aliceSignup.setMobileNumber("919999999999");
        aliceSignup.setEmail("alice@gmail.com");

        ResponseEntity<Map> aliceResponse = restTemplate.postForEntity(
                "/api/v1/auth/signup", aliceSignup, Map.class);
        assertEquals(HttpStatus.CREATED, aliceResponse.getStatusCode());
        assertEquals("OTP_SENT", aliceResponse.getBody().get("status"));

        User aliceUserDb = userRepository.findByUsername("alice").orElseThrow();
        String aliceOtp = aliceUserDb.getOtp();
        assertNotNull(aliceOtp);

        VerifyOtpRequestVO verifyAliceOtp = new VerifyOtpRequestVO();
        verifyAliceOtp.setUsername("alice");
        verifyAliceOtp.setOtp(aliceOtp);

        ResponseEntity<AuthResponseVO> verifyAliceResponse = restTemplate.postForEntity(
                "/api/v1/auth/verify-otp", verifyAliceOtp, AuthResponseVO.class);
        assertEquals(HttpStatus.OK, verifyAliceResponse.getStatusCode());
        assertNotNull(verifyAliceResponse.getBody().getToken());

        SignupRequestVO bobSignup = new SignupRequestVO();
        bobSignup.setUsername("bob");
        bobSignup.setPassword("password123");
        bobSignup.setDisplayName("Builder"); // Keep distinct from search tokens
        bobSignup.setAvatarUrl("bob.png");
        bobSignup.setMobileNumber("918888888888");
        bobSignup.setEmail("bob@gmail.com");

        ResponseEntity<Map> bobResponse = restTemplate.postForEntity(
                "/api/v1/auth/signup", bobSignup, Map.class);
        assertEquals(HttpStatus.CREATED, bobResponse.getStatusCode());
        assertEquals("OTP_SENT", bobResponse.getBody().get("status"));

        User bobUserDb = userRepository.findByUsername("bob").orElseThrow();
        String bobOtp = bobUserDb.getOtp();
        assertNotNull(bobOtp);

        VerifyOtpRequestVO verifyBobOtp = new VerifyOtpRequestVO();
        verifyBobOtp.setUsername("bob");
        verifyBobOtp.setOtp(bobOtp);

        ResponseEntity<AuthResponseVO> verifyBobResponse = restTemplate.postForEntity(
                "/api/v1/auth/verify-otp", verifyBobOtp, AuthResponseVO.class);
        assertEquals(HttpStatus.OK, verifyBobResponse.getStatusCode());
        assertNotNull(verifyBobResponse.getBody().getToken());

        // --- 2. LOGIN ALICE WITH OTP & VERIFY EMAIL LOG ---
        LoginRequestVO aliceLogin = new LoginRequestVO();
        aliceLogin.setUsername("alice");
        aliceLogin.setPassword("password123");

        ResponseEntity<Map> loginStep1 = restTemplate.postForEntity(
                "/api/v1/auth/login", aliceLogin, Map.class);
        assertEquals(HttpStatus.OK, loginStep1.getStatusCode());
        assertEquals("OTP_SENT", loginStep1.getBody().get("status"));

        // Check MongoDB for the sent email log
        List<EmailLog> emailLogs = emailLogRepository.findAll();
        assertFalse(emailLogs.isEmpty());
        EmailLog latestLog = emailLogs.get(emailLogs.size() - 1);
        assertEquals("alice@gmail.com", latestLog.getRecipient());
        assertTrue(latestLog.getSubject().contains("OTP"));
        assertNotNull(latestLog.getSentAt());

        // Retrieve OTP code from DB user entity
        User aliceUser = userRepository.findByUsername("alice").orElseThrow();
        String otp = aliceUser.getOtp();
        assertNotNull(otp);

        // Verify OTP
        VerifyOtpRequestVO verifyRequest = new VerifyOtpRequestVO();
        verifyRequest.setUsername("alice");
        verifyRequest.setOtp(otp);

        ResponseEntity<AuthResponseVO> verifyResponse = restTemplate.postForEntity(
                "/api/v1/auth/verify-otp", verifyRequest, AuthResponseVO.class);
        assertEquals(HttpStatus.OK, verifyResponse.getStatusCode());
        String aliceToken = verifyResponse.getBody().getToken();
        assertNotNull(aliceToken);

        // Define auth headers
        HttpHeaders aliceHeaders = new HttpHeaders();
        aliceHeaders.setBearerAuth(aliceToken);

        // --- 3. ALICE ADDS BOB AS CONTACT ---
        AddContactRequestVO addContact = new AddContactRequestVO();
        addContact.setMobileNumber("918888888888");
        addContact.setContactName("Bobby"); // custom alias!

        HttpEntity<AddContactRequestVO> addRequest = new HttpEntity<>(addContact, aliceHeaders);
        ResponseEntity<ContactDTO> addContactResponse = restTemplate.postForEntity(
                "/api/v1/contacts", addRequest, ContactDTO.class);
        assertEquals(HttpStatus.CREATED, addContactResponse.getStatusCode());
        assertEquals("Bobby", addContactResponse.getBody().getSavedContactName());

        // --- 4. ALICE STARTS PERSONAL DM WITH BOB ---
        CreatePersonalRoomRequestVO personalRequest = new CreatePersonalRoomRequestVO();
        personalRequest.setRecipientUsername("bob");

        HttpEntity<CreatePersonalRoomRequestVO> createDmRequest = new HttpEntity<>(personalRequest, aliceHeaders);
        ResponseEntity<RoomDTO> dmResponse = restTemplate.postForEntity(
                "/api/v1/rooms/personal", createDmRequest, RoomDTO.class);
        assertEquals(HttpStatus.CREATED, dmResponse.getStatusCode());
        assertFalse(dmResponse.getBody().isGroup());
        assertEquals("Bobby", dmResponse.getBody().getRoomName()); // Should be dynamically replaced by Bob's contact alias!
        assertEquals("Bobby", dmResponse.getBody().getRecipient().getDisplayName()); // Recipient's display name overridden as well!

        String roomId = dmResponse.getBody().getRoomId();

        // --- 5. SEARCH & CHAT LIST TESTING ---
        // Search by Display Name "Builder" -> Should find it
        HttpEntity<Void> getRequest = new HttpEntity<>(aliceHeaders);
        ResponseEntity<RoomDTO[]> searchResponse = restTemplate.exchange(
                "/api/v1/rooms/?search=builder", HttpMethod.GET, getRequest, RoomDTO[].class);
        assertEquals(HttpStatus.OK, searchResponse.getStatusCode());
        assertEquals(1, searchResponse.getBody().length);
        assertEquals(roomId, searchResponse.getBody()[0].getRoomId());

        // Search by Contact Number "918888888888" -> Should find it
        searchResponse = restTemplate.exchange(
                "/api/v1/rooms/?search=918888888888", HttpMethod.GET, getRequest, RoomDTO[].class);
        assertEquals(HttpStatus.OK, searchResponse.getStatusCode());
        assertEquals(1, searchResponse.getBody().length);

        // Search by Contact Alias "Bobby" -> Should find it
        searchResponse = restTemplate.exchange(
                "/api/v1/rooms/?search=bobby", HttpMethod.GET, getRequest, RoomDTO[].class);
        assertEquals(HttpStatus.OK, searchResponse.getStatusCode());
        assertEquals(1, searchResponse.getBody().length);

        // Search by Bob's username "bob" -> Should NOT match (since search by username is excluded)
        searchResponse = restTemplate.exchange(
                "/api/v1/rooms/?search=bob", HttpMethod.GET, getRequest, RoomDTO[].class);
        assertEquals(HttpStatus.OK, searchResponse.getStatusCode());
        assertEquals(0, searchResponse.getBody().length);

        // --- 6. EMOJI MESSAGE SAVING & RETRIEVAL ---
        // Post a message with emojis: 😂🔥👍
        Message msg = new Message();
        msg.setRoomId(roomId);
        msg.setSenderId("alice");
        msg.setContent("Hello 😂🔥👍");
        msg.setSentAt(java.time.LocalDateTime.now());
        mongoTemplate.save(msg);

        // Fetch messages for this room
        ResponseEntity<Map> messagesResponse = restTemplate.exchange(
                "/api/v1/rooms/" + roomId + "/messages", HttpMethod.GET, getRequest, Map.class);
        assertEquals(HttpStatus.OK, messagesResponse.getStatusCode());
        List<Map> messages = (List<Map>) messagesResponse.getBody().get("messages");
        assertFalse(messages.isEmpty());
        assertEquals("Hello 😂🔥👍", messages.get(messages.size() - 1).get("content"));
    }
}
