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

    @Value("${resend.api.url:https://api.resend.com/emails}")
    private String resendApiUrl;

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
                "Hello,\n\nYour OTP verification code is: %s\n\nThis code will expire in 10 minutes.\n\nThank you,\nChatmosphere Team",
                otp
        );

        log.info("========================================");
        log.info("Generating OTP code: {} for email: {}", otp, email);
        log.info("========================================");

        // 1. Try Resend HTTP API
        if (StringUtils.hasText(resendApiKey)) {
            try {
                log.info("Attempting to send OTP via Resend HTTP API to {}...", email);
                sendEmailViaResend(email, subject, body, null);
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

    /**
     * Sends a registration verification link.
     */
    @Override
    public void sendVerificationLink(String email, String link) {
        String subject = "Chatmosphere - Verify Your Email Address";
        String textBody = "Hello,\n\nPlease verify your email address by opening the following link in your browser:\n" + link + "\n\nThis link will expire in 15 minutes.\n\nThank you,\nChatmosphere Team";
        String htmlBody = String.format(
                "<div style=\"font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 550px; margin: 0 auto; padding: 30px; background: #0f172a; border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 16px; color: #f8fafc; text-align: center; box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);\">" +
                "  <h2 style=\"margin-top: 0; color: #ff9800; font-size: 24px; font-weight: 700; letter-spacing: 0.5px;\">Verify Your Email Address</h2>" +
                "  <p style=\"font-size: 15px; line-height: 1.6; color: #cbd5e1; margin-bottom: 25px;\">Thank you for joining Chatmosphere. Please verify your email to unlock your registration.</p>" +
                "  <div style=\"margin: 30px 0;\">" +
                "    <a href=\"%s\" style=\"background: linear-gradient(135deg, #6366f1 0%%, #4f46e5 100%%); color: #ffffff; padding: 12px 30px; font-size: 15px; font-weight: 600; text-decoration: none; border-radius: 10px; display: inline-block; box-shadow: 0 4px 15px rgba(99, 102, 241, 0.4);\">Verify Email Address</a>" +
                "  </div>" +
                "  <p style=\"font-size: 12px; color: #94a3b8; line-height: 1.5; margin-top: 25px;\">If the button above does not work, copy and paste the URL below into your browser:<br/>" +
                "  <a href=\"%s\" style=\"color: #6366f1; text-decoration: underline; word-break: break-all;\">%s</a></p>" +
                "  <hr style=\"border: none; border-top: 1px solid rgba(255, 255, 255, 0.1); margin: 25px 0;\"/>" +
                "  <p style=\"font-size: 11px; color: #64748b;\">This link is valid for 15 minutes. If you did not request this, you can safely ignore this email.</p>" +
                "</div>",
                link, link, link
        );

        log.info("========================================");
        log.info("Generating verification link for email: {}", email);
        log.info("========================================");

        // 1. Try Resend HTTP API
        if (StringUtils.hasText(resendApiKey)) {
            try {
                log.info("Attempting to send verification link via Resend HTTP API to {}...", email);
                sendEmailViaResend(email, subject, textBody, htmlBody);
                saveEmailLog(email, subject, textBody, "SENT (RESEND_LINK)", null);
                return;
            } catch (Exception e) {
                log.error("Failed to send verification link via Resend API: {}. Checking fallbacks...", e.getMessage());
            }
        }

        // 2. Try SMTP
        if (StringUtils.hasText(mailUsername)) {
            try {
                log.info("Attempting to send verification link via SMTP to {}...", email);
                sendHtmlEmailViaSmtp(email, subject, textBody, htmlBody);
                saveEmailLog(email, subject, textBody, "SENT (SMTP_LINK)", null);
                return;
            } catch (Exception e) {
                log.error("Failed to send verification link via SMTP: {}. Falling back to Console Logging.", e.getMessage());
            }
        }

        // 3. Fallback to console print
        String reason = !StringUtils.hasText(resendApiKey) && !StringUtils.hasText(mailUsername)
                ? "Neither Resend nor SMTP is configured."
                : "Both Resend and SMTP attempts failed.";
        log.warn("============================================================");
        log.warn(">>> MOCK EMAIL VERIFICATION LINK DELIVERY");
        log.warn(">>> Recipient : {}", email);
        log.warn(">>> Link      : {}", link);
        log.warn("============================================================");
        System.out.println("\n[CHATMOSPHERE VERIFICATION LINK] " + email + " → " + link + "\n");
        saveEmailLog(email, subject, textBody, "FAILED (MOCK_LINK)", reason);
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    /**
     * Sends an email via the Resend REST API using java.net.http.HttpClient.
     */
    private void sendEmailViaResend(String email, String subject, String textBody, String htmlBody) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("from", resendFromEmail);
        payload.put("to", Collections.singletonList(email));
        payload.put("subject", subject);
        if (textBody != null) {
            payload.put("text", textBody);
        }
        if (htmlBody != null) {
            payload.put("html", htmlBody);
        }

        String jsonPayload = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resendApiUrl))
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
        log.info("Email successfully transmitted to {} via Resend API", email);
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
     * Sends an HTML email via SMTP using MimeMessageHelper.
     */
    private void sendHtmlEmailViaSmtp(String email, String subject, String textBody, String htmlBody) throws Exception {
        jakarta.mail.internet.MimeMessage mimeMessage = mailSender.createMimeMessage();
        org.springframework.mail.javamail.MimeMessageHelper helper =
                new org.springframework.mail.javamail.MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom(mailUsername);
        helper.setTo(email);
        helper.setSubject(subject);
        helper.setText(textBody, htmlBody);

        mailSender.send(mimeMessage);
        log.info("HTML email successfully transmitted to {} via SMTP", email);
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
