package com.chatmosphere.backend.controller;

import com.chatmosphere.backend.documents.Message;
import com.chatmosphere.backend.model.MessageRequest;
import com.chatmosphere.backend.repository.RoomsRepository;
import com.chatmosphere.backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
@CrossOrigin("http://localhost:5173")
@RequiredArgsConstructor
public class ChatController {

    public final ChatService chatService;

    /*
     Sending and Receiving the messages
     */
    @MessageMapping("sendMessage{roomId}") //receives message from client
    @SendTo("topic/room/{roomId}") //client subscribe and message will be published here
    public Message sendMessage(@DestinationVariable String roomId,
                               @RequestBody MessageRequest messageRequest) {
        return chatService.sendMessages(messageRequest);
    }
}
