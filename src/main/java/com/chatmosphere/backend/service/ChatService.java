package com.chatmosphere.backend.service;

import com.chatmosphere.backend.documents.Message;
import com.chatmosphere.backend.documents.Room;
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

    /**
     * Persists an incoming chat message, updates room/metadata timestamps, and returns the message DTO.
     */
    public MessageDTO sendMessages(MessageRequest messageRequest) {
        // 1. Validate that the room exists
        Room room = roomsService.findRoomByIdOrElseThrow(messageRequest.getRoomId());

        // 2. Construct the message document
        LocalDateTime sentAt = LocalDateTime.now(ZoneOffset.UTC);
        Message message = buildMessage(messageRequest, sentAt);

        // 3. Update active activity timestamps across Mongo and SQL schemas
        roomsService.updateRoomAndMetaActivity(room, sentAt);

        // 4. Save and return DTO representation
        Message savedMessage = messageRepository.save(message);
        return roomsService.mapToMessageDTO(savedMessage);
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    private Message buildMessage(MessageRequest request, LocalDateTime sentAt) {
        Message message = new Message();
        message.setRoomId(request.getRoomId());
        message.setSenderId(request.getSenderId());
        message.setContent(request.getMessageContent());
        message.setSentAt(sentAt);
        return message;
    }
}
