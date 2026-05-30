package com.chatmosphere.backend.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePersonalRoomRequestVO {

    @NotBlank(message = "Recipient username cannot be empty")
    private String recipientUsername;
}
