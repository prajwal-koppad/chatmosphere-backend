package com.chatmosphere.backend.documents;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "rooms")
public class Room extends Audit {

    @Id
    private String id;

    private String roomId;

    private String roomName;

    private List<Message> messages = new ArrayList<>();
}






