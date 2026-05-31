package com.chatmosphere.backend.controller;

import com.chatmosphere.backend.service.UserService;
import com.chatmosphere.backend.vo.AuthResponseVO;
import com.chatmosphere.backend.vo.LoginRequestVO;
import com.chatmosphere.backend.vo.SignupRequestVO;
import com.chatmosphere.backend.vo.VerifyOtpRequestVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/signup/send-verification")
    public ResponseEntity<Map<String, String>> sendSignupVerification(@RequestParam String email) {
        Map<String, String> response = userService.sendSignupVerification(email);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/signup/verify")
    public ResponseEntity<String> verifySignupEmailToken(@RequestParam String token) {
        String htmlResponse = userService.verifySignupEmailToken(token);
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(htmlResponse);
    }

    @GetMapping("/signup/check-verification")
    public ResponseEntity<Map<String, Boolean>> checkSignupVerification(@RequestParam String email) {
        Map<String, Boolean> response = userService.checkSignupVerification(email);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponseVO> signup(@Valid @RequestBody SignupRequestVO signupRequest) {
        AuthResponseVO response = userService.signup(signupRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequestVO loginRequest) {
        Map<String, String> response = userService.login(loginRequest);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/login/resend-otp")
    public ResponseEntity<Map<String, String>> resendLoginOtp(@RequestParam String username) {
        Map<String, String> response = userService.resendLoginOtp(username);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponseVO> verifyOtp(@Valid @RequestBody VerifyOtpRequestVO verifyOtpRequest) {
        AuthResponseVO response = userService.verifyOtp(verifyOtpRequest);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}

