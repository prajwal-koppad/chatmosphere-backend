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

    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(@Valid @RequestBody SignupRequestVO signupRequest) {
        Map<String, String> response = userService.signup(signupRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequestVO loginRequest) {
        Map<String, String> response = userService.login(loginRequest);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponseVO> verifyOtp(@Valid @RequestBody VerifyOtpRequestVO verifyOtpRequest) {
        AuthResponseVO response = userService.verifyOtp(verifyOtpRequest);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
