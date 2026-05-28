package com.chatmosphere.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private String id;
    private String roomId;
    private String senderId;
    private String content;
    private LocalDateTime sentAt;
}
