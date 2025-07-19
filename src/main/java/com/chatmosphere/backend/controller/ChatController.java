package com.chatmosphere.backend.controller;

import com.chatmosphere.backend.model.MessageRequest;
import com.chatmosphere.backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    /*
     Sending and Receiving the messages
     */
    @MessageMapping("sendMessage/{roomId}") //receives message from client
    public void sendMessage(@DestinationVariable String roomId,
                               @RequestBody MessageRequest messageRequest) {

        messagingTemplate.convertAndSend("/topic/room/" + roomId, chatService.sendMessages(messageRequest));
    }
}
