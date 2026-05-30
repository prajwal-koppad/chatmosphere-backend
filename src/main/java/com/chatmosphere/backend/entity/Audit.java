package com.chatmosphere.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public class Audit {

    private String createdBy;

    private LocalDateTime createDate;

    private String updatedBy;

    private LocalDateTime updateDate;
}
