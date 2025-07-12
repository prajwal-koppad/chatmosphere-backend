package com.chatmosphere.backend.documents;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Audit {

    private String createdBy;

    private LocalDateTime createDate;

    private String updatedBy;

    private LocalDateTime updateDate;
}
