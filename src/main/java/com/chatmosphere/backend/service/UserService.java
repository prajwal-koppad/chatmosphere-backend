package com.chatmosphere.backend.service;

import com.chatmosphere.backend.entity.User;
import com.chatmosphere.backend.repository.UserRepository;
import com.chatmosphere.backend.security.JwtUtils;
import com.chatmosphere.backend.vo.AuthResponseVO;
import com.chatmosphere.backend.vo.LoginRequestVO;
import com.chatmosphere.backend.vo.SignupRequestVO;
import com.chatmosphere.backend.dto.UserDTO;
import com.chatmosphere.backend.vo.VerifyOtpRequestVO;
import lombok.RequiredArgsConstructor;
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
public class UserService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final EmailService emailService;

    // =========================================================================
    // Core Business Methods
    // =========================================================================

    /**
     * Registers a new user after verifying credential uniqueness.
     */
    public Map<String, String> signup(SignupRequestVO signupRequest) {
        validateSignupUniqueness(signupRequest);

        User user = new User();
        user.setUsername(signupRequest.getUsername());
        user.setPassword(passwordEncoder.encode(signupRequest.getPassword()));
        user.setDisplayName(signupRequest.getDisplayName());
        user.setAvatarUrl(signupRequest.getAvatarUrl());
        user.setMobileNumber(signupRequest.getMobileNumber());
        user.setEmail(signupRequest.getEmail());
        user.setCreateDate(LocalDateTime.now(ZoneOffset.UTC));
        user.setCreatedBy(signupRequest.getUsername());

        // Generate a cryptographically secure 6-digit verification code
        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        user.setOtp(otp);
        user.setOtpExpiry(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5));

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
     * Validates credentials and generates/sends a 6-digit OTP code.
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
        user.setOtpExpiry(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5));
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
