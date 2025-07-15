package com.chatmosphere.backend.service;

import com.chatmosphere.backend.documents.Message;
import com.chatmosphere.backend.documents.Room;
import com.chatmosphere.backend.model.MessageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final RoomsService roomsService;

    public Message sendMessages(MessageRequest messageRequest) {
        Room room = roomsService.findRoomByIdOrElseThrow(messageRequest.getRoomId());

        Message message = new Message();
        message.setSenderId(messageRequest.getSenderId());
        message.setContent(messageRequest.getMessageContent());
        message.setSentAt(LocalDateTime.now());

        room.getMessages().add(message);
        roomsService.saveRoom(room);
        return message;
    }
}
