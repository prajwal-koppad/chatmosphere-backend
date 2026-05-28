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
public class RoomDTO {
    private String id;
    private String roomId;
    private String roomName;
    private String createdBy;
    private LocalDateTime createDate;
    private String updatedBy;
    private LocalDateTime updateDate;
}
