package com.chatmosphere.backend.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponseVO {
    private String token;
    private String username;
    private String displayName;
    private String avatarUrl;
}
