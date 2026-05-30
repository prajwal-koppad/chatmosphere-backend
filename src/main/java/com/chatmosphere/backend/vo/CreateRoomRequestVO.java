package com.chatmosphere.backend.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateRoomRequestVO {

    @NotBlank(message = "Room ID cannot be empty")
    @Size(min = 3, max = 50, message = "Room ID must be between 3 and 50 characters")
    private String roomId;

    @NotBlank(message = "Room Name cannot be empty")
    @Size(min = 3, max = 100, message = "Room Name must be between 3 and 100 characters")
    private String roomName;

    private java.util.Set<String> participantUsernames = new java.util.HashSet<>();
}
