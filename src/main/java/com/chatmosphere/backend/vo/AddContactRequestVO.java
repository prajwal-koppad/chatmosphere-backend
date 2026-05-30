package com.chatmosphere.backend.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddContactRequestVO {

    @NotBlank(message = "Contact mobile number cannot be empty")
    private String mobileNumber;

    @NotBlank(message = "Contact alias name cannot be empty")
    private String contactName;
}
