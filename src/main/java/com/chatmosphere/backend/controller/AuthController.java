package com.chatmosphere.backend.controller;

import com.chatmosphere.backend.service.UserService;
import com.chatmosphere.backend.vo.AuthResponseVO;
import com.chatmosphere.backend.vo.LoginRequestVO;
import com.chatmosphere.backend.vo.SignupRequestVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponseVO> signup(@Valid @RequestBody SignupRequestVO signupRequest) {
        AuthResponseVO response = userService.signup(signupRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseVO> login(@Valid @RequestBody LoginRequestVO loginRequest) {
        AuthResponseVO response = userService.login(loginRequest);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
