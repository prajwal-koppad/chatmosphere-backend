package com.chatmosphere.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContactDTO {
    private Long id;
    private String contactUsername;
    private String defaultDisplayName;
    private String savedContactName;
    private String mobileNumber;
    private String avatarUrl;
}
