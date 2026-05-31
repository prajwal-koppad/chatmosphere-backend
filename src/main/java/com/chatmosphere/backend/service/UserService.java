package com.chatmosphere.backend.service;

import com.chatmosphere.backend.dto.UserDTO;
import com.chatmosphere.backend.entity.PendingVerification;
import com.chatmosphere.backend.entity.User;
import com.chatmosphere.backend.repository.PendingVerificationRepository;
import com.chatmosphere.backend.repository.UserRepository;
import com.chatmosphere.backend.security.JwtUtils;
import com.chatmosphere.backend.vo.AuthResponseVO;
import com.chatmosphere.backend.vo.LoginRequestVO;
import com.chatmosphere.backend.vo.SignupRequestVO;
import com.chatmosphere.backend.vo.VerifyOtpRequestVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PendingVerificationRepository pendingVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final EmailService emailService;

    @Value("${backend.base.url:http://localhost:8080}")
    private String backendBaseUrl;

    // =========================================================================
    // Core Business Methods
    // =========================================================================

    /**
     * Sends a signup email verification link.
     */
    public Map<String, String> sendSignupVerification(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already registered");
        }

        String token = java.util.UUID.randomUUID().toString();
        PendingVerification verification = pendingVerificationRepository.findByEmail(email)
                .orElse(new PendingVerification());

        verification.setEmail(email);
        verification.setToken(token);
        verification.setVerified(false);
        verification.setExpiryTime(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15)); // 15 mins expiry
        pendingVerificationRepository.save(verification);

        String link = backendBaseUrl + "/api/v1/auth/signup/verify?token=" + token;
        emailService.sendVerificationLink(email, link);

        return Map.of(
                "status", "VERIFICATION_LINK_SENT",
                "message", "Verification email sent. Please check your inbox and verify your email."
        );
    }

    /**
     * Verifies the signup verification token and updates its state.
     */
    public String verifySignupEmailToken(String token) {
        PendingVerification verification = pendingVerificationRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification token not found"));

        if (verification.getExpiryTime().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            return "<html><body style=\"font-family: 'Segoe UI', sans-serif; background-color: #0f172a; color: #f8fafc; text-align: center; padding-top: 100px;\">" +
                    "<div style=\"display: inline-block; padding: 40px; background-color: #1e293b; border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); max-width: 450px;\">" +
                    "<h1 style=\"color: #ef4444; margin-top:0;\">Link Expired</h1>" +
                    "<p style=\"color: #cbd5e1; font-size: 15px; line-height: 1.6;\">This email verification link has expired (it was valid for 15 minutes). Please return to the app and request a new one.</p>" +
                    "</div></body></html>";
        }

        verification.setVerified(true);
        pendingVerificationRepository.save(verification);

        return "<html><body style=\"font-family: 'Segoe UI', sans-serif; background-color: #0f172a; color: #f8fafc; text-align: center; padding-top: 100px;\">" +
                "<div style=\"display: inline-block; padding: 40px; background-color: #1e293b; border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); max-width: 450px;\">" +
                "<h1 style=\"color: #22c55e; margin-top:0;\">Email Verified!</h1>" +
                "<p style=\"color: #cbd5e1; font-size: 15px; line-height: 1.6;\">Your email has been successfully verified. You can now close this tab, return to the sign-up page, and complete your registration details.</p>" +
                "</div></body></html>";
    }

    /**
     * Checks if the signup email is verified.
     */
    public Map<String, Boolean> checkSignupVerification(String email) {
        PendingVerification verification = pendingVerificationRepository.findByEmail(email).orElse(null);
        boolean isVerified = verification != null && verification.isVerified() &&
                !verification.getExpiryTime().isBefore(LocalDateTime.now(ZoneOffset.UTC));
        return Map.of("verified", isVerified);
    }

    /**
     * Registers a new user using a verified email.
     */
    public AuthResponseVO signup(SignupRequestVO signupRequest) {
        validateSignupUniqueness(signupRequest);

        PendingVerification verification = pendingVerificationRepository.findByEmail(signupRequest.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email has not been verified yet"));

        if (!verification.isVerified()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email has not been verified yet");
        }

        if (verification.getExpiryTime().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email verification has expired. Please verify again.");
        }

        User user = new User();
        user.setUsername(signupRequest.getUsername());
        user.setPassword(passwordEncoder.encode(signupRequest.getPassword()));
        user.setDisplayName(signupRequest.getDisplayName());
        user.setAvatarUrl(signupRequest.getAvatarUrl());
        user.setMobileNumber(signupRequest.getMobileNumber());
        user.setEmail(signupRequest.getEmail());
        user.setCreateDate(LocalDateTime.now(ZoneOffset.UTC));
        user.setCreatedBy(signupRequest.getUsername());

        userRepository.save(user);

        // Delete the verification record on successful signup
        pendingVerificationRepository.delete(verification);

        // Generate token and return session data directly (email was already verified)
        String token = jwtUtils.generateToken(user.getUsername());
        return new AuthResponseVO(
                token,
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl()
        );
    }

    /**
     * Validates credentials and generates/sends a 6-digit OTP code (expiring in 10 minutes).
     */
    public Map<String, String> login(LoginRequestVO loginRequest) {
        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        // Generate a cryptographically secure 6-digit verification code
        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        user.setOtp(otp);
        user.setOtpExpiry(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10)); // 10 minutes expiry
        userRepository.save(user);

        // Send OTP via configured handler
        emailService.sendOtp(user.getEmail(), otp);

        return Map.of(
                "status", "OTP_SENT",
                "username", user.getUsername(),
                "message", "Verification OTP sent to your registered email address"
        );
    }

    /**
     * Resends a login OTP.
     */
    public Map<String, String> resendLoginOtp(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Generate a new 6-digit verification code
        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        user.setOtp(otp);
        user.setOtpExpiry(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10)); // 10 minutes expiry
        userRepository.save(user);

        // Send OTP
        emailService.sendOtp(user.getEmail(), otp);

        return Map.of(
                "status", "OTP_SENT",
                "username", user.getUsername(),
                "message", "Verification OTP resent to your registered email address"
        );
    }

    /**
     * Verifies the submitted OTP code and issues a JWT session token.
     */
    public AuthResponseVO verifyOtp(VerifyOtpRequestVO verifyOtpRequest) {
        User user = userRepository.findByUsername(verifyOtpRequest.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        validateOtpValidity(user, verifyOtpRequest.getOtp());

        // Clear OTP on successful verification
        user.setOtp(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        String token = jwtUtils.generateToken(user.getUsername());
        return new AuthResponseVO(
                token,
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl()
        );
    }

    /**
     * Retrieves all other registered users (excluding the current user).
     */
    public List<UserDTO> getAllOtherUsers(String currentUsername) {
        return userRepository.findByUsernameNot(currentUsername).stream()
                .map(user -> new UserDTO(
                        user.getUsername(),
                        user.getDisplayName(),
                        user.getAvatarUrl(),
                        user.getMobileNumber(),
                        user.getEmail()
                ))
                .toList();
    }

    // =========================================================================
    // Helper / Validation Methods
    // =========================================================================

    private void validateSignupUniqueness(SignupRequestVO signupRequest) {
        if (userRepository.findByUsername(signupRequest.getUsername()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already exists");
        }
        if (userRepository.findByMobileNumber(signupRequest.getMobileNumber()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mobile number already exists");
        }
        if (userRepository.findByEmail(signupRequest.getEmail()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already exists");
        }
    }

    private void validateOtpValidity(User user, String submittedOtp) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (user.getOtp() == null || user.getOtpExpiry() == null || user.getOtpExpiry().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OTP is expired or not requested");
        }
        if (!user.getOtp().equals(submittedOtp)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OTP code");
        }
    }
}

