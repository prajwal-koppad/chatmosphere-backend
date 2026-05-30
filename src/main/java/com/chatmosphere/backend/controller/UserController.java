package com.chatmosphere.backend.controller;

import com.chatmosphere.backend.dto.UserDTO;
import com.chatmosphere.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers(Principal principal) {
        List<UserDTO> users = userService.getAllOtherUsers(principal.getName());
        return ResponseEntity.ok(users);
    }
}
