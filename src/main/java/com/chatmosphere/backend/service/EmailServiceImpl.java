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

    @Value("${emailjs.service.id:}")
    private String emailJsServiceId;

    @Value("${emailjs.template.id:}")
    private String emailJsTemplateId;

    @Value("${emailjs.public.key:}")
    private String emailJsPublicKey;

    @Value("${emailjs.private.key:}")
    private String emailJsPrivateKey;

    @Value("${emailjs.api.url:https://api.emailjs.com/api/v1.0/email/send}")
    private String emailJsApiUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // =========================================================================
    // Public Operations
    // =========================================================================

    /**
     * Sends an OTP verification email to the user.
     */
    @Override
    public void sendOtp(String email, String otp) {
        String subject = "Chatmosphere - Your OTP Verification Code";
        String body = String.format(
                "Hello,\n\nYour OTP verification code is: %s\n\nThis code will expire in 10 minutes.\n\nThank you,\nChatmosphere Team",
                otp
        );
        log.info("Generating OTP code for email: {}", email);
        send(email, subject, body, null, "OTP", otp);
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
        log.info("Generating verification link for email: {}", email);
        send(email, subject, textBody, htmlBody, "VERIFICATION LINK", link);
    }

    // =========================================================================
    // Core Email Router
    // =========================================================================

    /**
     * Tries sending via EmailJS API, falls back to SMTP, and finally falls back to console.
     */
    private void send(String email, String subject, String textBody, String htmlBody, String type, String consoleValue) {
        // 1. EmailJS API
        if (StringUtils.hasText(emailJsServiceId) && StringUtils.hasText(emailJsTemplateId) && StringUtils.hasText(emailJsPublicKey)) {
            try {
                log.info("Attempting delivery of {} via EmailJS HTTP API to {}...", type, email);
                sendEmailViaEmailJs(email, subject, textBody, htmlBody);
                saveEmailLog(email, subject, textBody, "SENT (EMAILJS_" + type.replace(" ", "_") + ")", null);
                return;
            } catch (Exception e) {
                log.error("EmailJS HTTP API delivery failed: {}", e.getMessage());
            }
        }

        // 2. SMTP
        if (StringUtils.hasText(mailUsername)) {
            try {
                log.info("Attempting delivery of {} via SMTP to {}...", type, email);
                if (htmlBody != null) {
                    sendHtmlEmailViaSmtp(email, subject, textBody, htmlBody);
                } else {
                    sendEmailViaSmtp(email, subject, textBody);
                }
                saveEmailLog(email, subject, textBody, "SENT (SMTP_" + type.replace(" ", "_") + ")", null);
                return;
            } catch (Exception e) {
                log.error("SMTP delivery failed: {}", e.getMessage());
            }
        }

        // 3. Console Fallback
        String reason = !StringUtils.hasText(emailJsServiceId) && !StringUtils.hasText(mailUsername)
                ? "Neither EmailJS nor SMTP is configured."
                : "Active delivery channels (EmailJS & SMTP) failed.";
        executeConsoleFallback(email, subject, textBody, type, consoleValue, "FAILED", reason);
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    /**
     * Sends an email via the EmailJS REST API using HttpClient.
     */
    private void sendEmailViaEmailJs(String email, String subject, String textBody, String htmlBody) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("service_id", emailJsServiceId);
        payload.put("template_id", emailJsTemplateId);
        payload.put("user_id", emailJsPublicKey);
        if (StringUtils.hasText(emailJsPrivateKey)) {
            payload.put("accessToken", emailJsPrivateKey);
        }

        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("to_email", email);
        templateParams.put("subject", subject);
        templateParams.put("text_body", textBody);
        templateParams.put("html_body", htmlBody != null ? htmlBody : textBody);
        payload.put("template_params", templateParams);

        String jsonPayload = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(emailJsApiUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("HTTP Status " + response.statusCode() + ": " + response.body());
        }
        log.info("Email delivered successfully via EmailJS API");
    }



    /**
     * Transmits a simple SMTP message.
     */
    private void sendEmailViaSmtp(String email, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(email);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.info("Email delivered successfully via SMTP");
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
        log.info("HTML email delivered successfully via SMTP");
    }

    /**
     * Outputs email details to Console and logs the fallback event to Mongo.
     */
    private void executeConsoleFallback(String email, String subject, String body, String type, String value, String status, String errorMessage) {
        log.warn("============================================================");
        log.warn(">>> MOCK {} DELIVERY — Active delivery channels unavailable", type);
        log.warn(">>> Recipient : {}", email);
        log.warn(">>> Value     : {}", value);
        log.warn(">>> Status    : {}", status);
        if (errorMessage != null) log.warn(">>> Reason    : {}", errorMessage);
        log.warn("============================================================");
        System.out.println("\n[CHATMOSPHERE " + type.toUpperCase() + "] " + email + " → " + value + "\n");
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

