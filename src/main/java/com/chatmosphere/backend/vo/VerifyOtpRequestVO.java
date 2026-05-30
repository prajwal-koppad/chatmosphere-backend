package com.chatmosphere.backend.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyOtpRequestVO {

    @NotBlank(message = "Username cannot be empty")
    private String username;

    @NotBlank(message = "OTP code cannot be empty")
    private String otp;
}
