package com.chatmosphere.backend.service;

import com.chatmosphere.backend.documents.Message;
import com.chatmosphere.backend.dto.MessageDTO;
import com.chatmosphere.backend.model.MessageRequest;
import com.chatmosphere.backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final RoomsService roomsService;
    private final MessageRepository messageRepository;

    public MessageDTO sendMessages(MessageRequest messageRequest) {
        // Validate room exists
        roomsService.findRoomByIdOrElseThrow(messageRequest.getRoomId());

        Message message = new Message();
        message.setRoomId(messageRequest.getRoomId());
        message.setSenderId(messageRequest.getSenderId());
        message.setContent(messageRequest.getMessageContent());
        message.setSentAt(LocalDateTime.now(ZoneOffset.UTC));

        Message savedMessage = messageRepository.save(message);
        return roomsService.mapToMessageDTO(savedMessage);
    }
}
