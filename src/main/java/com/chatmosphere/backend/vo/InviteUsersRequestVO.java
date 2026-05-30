package com.chatmosphere.backend.vo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class InviteUsersRequestVO {

    @NotEmpty(message = "Usernames list must not be empty")
    private Set<String> usernames;
}
