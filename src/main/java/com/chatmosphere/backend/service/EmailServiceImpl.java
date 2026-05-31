package com.chatmosphere.backend.service;

import com.chatmosphere.backend.documents.EmailLog;
import com.chatmosphere.backend.repository.EmailLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailLogRepository emailLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${resend.api.key:}")
    private String resendApiKey;

    @Value("${resend.from.email:onboarding@resend.dev}")
    private String resendFromEmail;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // =========================================================================
    // Public Operations
    // =========================================================================

    /**
     * Sends an OTP verification email to the user.
     * Uses Resend HTTP API as primary if configured, falls back to SMTP, and finally console mock.
     */
    @Override
    public void sendOtp(String email, String otp) {
        String subject = "Chatmosphere - Your OTP Verification Code";
        String body = String.format(
                "Hello,\n\nYour OTP verification code is: %s\n\nThis code will expire in 5 minutes.\n\nThank you,\nChatmosphere Team",
                otp
        );

        log.info("========================================");
        log.info("Generating OTP code: {} for email: {}", otp, email);
        log.info("========================================");

        // 1. Try Resend HTTP API
        if (StringUtils.hasText(resendApiKey)) {
            try {
                log.info("Attempting to send OTP via Resend HTTP API to {}...", email);
                sendEmailViaResend(email, subject, body);
                saveEmailLog(email, subject, body, "SENT (RESEND)", null);
                return;
            } catch (Exception e) {
                log.error("Failed to send OTP via Resend API: {}. Checking fallbacks...", e.getMessage());
            }
        }

        // 2. Try SMTP
        if (StringUtils.hasText(mailUsername)) {
            try {
                log.info("Attempting to send OTP via SMTP to {}...", email);
                sendEmailViaSmtp(email, subject, body);
                saveEmailLog(email, subject, body, "SENT (SMTP)", null);
                return;
            } catch (Exception e) {
                log.error("Failed to send OTP via SMTP: {}. Falling back to Console Logging.", e.getMessage());
            }
        }

        // 3. Fallback to console print
        String reason = !StringUtils.hasText(resendApiKey) && !StringUtils.hasText(mailUsername)
                ? "Neither Resend nor SMTP is configured."
                : "Both Resend and SMTP attempts failed.";
        executeConsoleFallback(email, subject, body, otp, "FAILED", reason);
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    /**
     * Sends an email via the Resend REST API using java.net.http.HttpClient.
     */
    private void sendEmailViaResend(String email, String subject, String body) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("from", resendFromEmail);
        payload.put("to", Collections.singletonList(email));
        payload.put("subject", subject);
        payload.put("text", body);

        String jsonPayload = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("Resend API response status code: {}", response.statusCode());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Resend API returned non-success status code: " + response.statusCode() + ", body: " + response.body());
        }
        log.info("OTP email successfully transmitted to {} via Resend API", email);
    }

    /**
     * Constructs and transmits a simple SMTP message.
     */
    private void sendEmailViaSmtp(String email, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(email);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
        log.info("OTP email successfully transmitted to {} via SMTP", email);
    }

    /**
     * Outputs OTP details to System.out and logs the event.
     */
    private void executeConsoleFallback(String email, String subject, String body, String otp, String status, String errorMessage) {
        log.warn("============================================================");
        log.warn(">>> MOCK OTP DELIVERY — Active delivery channels failed or unavailable");
        log.warn(">>> Recipient : {}", email);
        log.warn(">>> OTP Code  : {}", otp);
        log.warn(">>> Status    : {}", status);
        if (errorMessage != null) log.warn(">>> Reason    : {}", errorMessage);
        log.warn("============================================================");
        System.out.println("\n[CHATMOSPHERE OTP] " + email + " → " + otp + "\n");
        saveEmailLog(email, subject, body, status, errorMessage);
    }

    /**
     * Logs the transaction status to MongoDB.
     */
    private void saveEmailLog(String email, String subject, String body, String status, String errorMessage) {
        try {
            EmailLog emailLog = new EmailLog();
            emailLog.setRecipient(email);
            emailLog.setSubject(subject);
            emailLog.setBody(body);
            emailLog.setSentAt(LocalDateTime.now(ZoneOffset.UTC));
            emailLog.setStatus(status);
            emailLog.setErrorMessage(errorMessage);
            emailLogRepository.save(emailLog);
            log.debug("Email log written to MongoDB: id={}", emailLog.getId());
        } catch (Exception ex) {
            log.error("Failed to write email log to MongoDB: {}", ex.getMessage());
        }
    }
}
