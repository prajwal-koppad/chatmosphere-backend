package com.chatmosphere.backend.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupRequestVO {

    @NotBlank(message = "Username cannot be empty")
    @Size(min = 4, max = 20, message = "Username must be between 4 and 20 characters")
    private String username;

    @NotBlank(message = "Password cannot be empty")
    @Size(min = 6, max = 40, message = "Password must be between 6 and 40 characters")
    private String password;

    @NotBlank(message = "Display name cannot be empty")
    @Size(min = 2, max = 50, message = "Display name must be between 2 and 50 characters")
    private String displayName;

    private String avatarUrl;

    @NotBlank(message = "Mobile number cannot be empty")
    private String mobileNumber;

    @NotBlank(message = "Email cannot be empty")
    @jakarta.validation.constraints.Email(message = "Invalid email format")
    private String email;
}
