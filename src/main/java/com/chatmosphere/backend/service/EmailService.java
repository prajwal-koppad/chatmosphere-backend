package com.chatmosphere.backend.service;

public interface EmailService {
    void sendOtp(String email, String otp);
    void sendVerificationLink(String email, String link);
}
