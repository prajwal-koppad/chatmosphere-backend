package com.chatmosphere.backend.service;

import com.chatmosphere.backend.documents.EmailLog;
import com.chatmosphere.backend.repository.EmailLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailLogRepository emailLogRepository;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    // =========================================================================
    // Public Operations
    // =========================================================================

    /**
     * Sends an OTP verification email to the user, with local fallback if SMTP is unconfigured.
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

        // Fallback to console print if SMTP username is missing
        if (!StringUtils.hasText(mailUsername)) {
            log.warn("SMTP credentials (spring.mail.username) missing! Falling back to Console Logging.");
            executeConsoleFallback(email, subject, body, otp, "SENT (MOCK)", null);
            return;
        }

        try {
            sendEmailViaSmtp(email, subject, body);
            saveEmailLog(email, subject, body, "SENT", null);
        } catch (Exception e) {
            log.error("Failed to send OTP via SMTP: {}. Falling back to Console Logging.", e.getMessage());
            executeConsoleFallback(email, subject, body, otp, "FAILED", e.getMessage());
        }
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

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
        log.info("OTP email successfully transmitted to {}", email);
    }

    /**
     * Outputs OTP details to System.out and logs the event.
     */
    private void executeConsoleFallback(String email, String subject, String body, String otp, String status, String errorMessage) {
        log.warn("============================================================");
        log.warn(">>> MOCK OTP DELIVERY — SMTP unavailable");
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
