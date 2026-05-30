package com.chatmosphere.backend.documents;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "emails")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailLog {

    @Id
    private String id;

    @Indexed
    private String recipient;

    private String subject;

    private String body;

    @Indexed
    private LocalDateTime sentAt;

    private String status; // "SENT" or "FAILED"

    private String errorMessage;
}
