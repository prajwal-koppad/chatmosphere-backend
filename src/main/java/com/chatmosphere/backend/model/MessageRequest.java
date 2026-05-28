package com.chatmosphere.backend.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class MessageRequest {

    @NotBlank(message = "Sender ID cannot be empty")
    private String senderId;

    @NotBlank(message = "Message content cannot be empty")
    private String messageContent;

    @NotBlank(message = "Room ID cannot be empty")
    private String roomId;
}
