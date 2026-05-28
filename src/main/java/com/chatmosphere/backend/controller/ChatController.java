package com.chatmosphere.backend.controller;

import com.chatmosphere.backend.model.MessageRequest;
import com.chatmosphere.backend.model.TypingRequest;
import com.chatmosphere.backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

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
                            @Payload MessageRequest messageRequest) {

        messagingTemplate.convertAndSend("/topic/room/" + roomId, chatService.sendMessages(messageRequest));
    }

    /*
     Broadcasting typing status
     */
    @MessageMapping("typing/{roomId}")
    public void typing(@DestinationVariable String roomId,
                       @Payload TypingRequest typingRequest) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/typing", typingRequest);
    }
}
