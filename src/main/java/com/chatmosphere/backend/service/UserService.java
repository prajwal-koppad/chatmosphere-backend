package com.chatmosphere.backend.service;

import com.chatmosphere.backend.entity.User;
import com.chatmosphere.backend.repository.UserRepository;
import com.chatmosphere.backend.security.JwtUtils;
import com.chatmosphere.backend.vo.AuthResponseVO;
import com.chatmosphere.backend.vo.LoginRequestVO;
import com.chatmosphere.backend.vo.SignupRequestVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AuthResponseVO signup(SignupRequestVO signupRequest) {
        if (userRepository.findByUsername(signupRequest.getUsername()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already exists");
        }

        User user = new User();
        user.setUsername(signupRequest.getUsername());
        user.setPassword(passwordEncoder.encode(signupRequest.getPassword()));
        user.setDisplayName(signupRequest.getDisplayName());
        user.setAvatarUrl(signupRequest.getAvatarUrl());
        user.setCreateDate(LocalDateTime.now(ZoneOffset.UTC));
        user.setCreatedBy(signupRequest.getUsername());

        userRepository.save(user);

        String token = jwtUtils.generateToken(user.getUsername());
        return new AuthResponseVO(
                token,
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl()
        );
    }

    public AuthResponseVO login(LoginRequestVO loginRequest) {
        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        String token = jwtUtils.generateToken(user.getUsername());
        return new AuthResponseVO(
                token,
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl()
        );
    }
}
