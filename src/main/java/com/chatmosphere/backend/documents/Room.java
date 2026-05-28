package com.chatmosphere.backend.documents;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "rooms")
public class Room extends Audit {

    @Id
    private String id;

    @Indexed(unique = true)
    private String roomId;

    private String roomName;
}






