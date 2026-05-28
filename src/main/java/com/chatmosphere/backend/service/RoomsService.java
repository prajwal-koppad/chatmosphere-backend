package com.chatmosphere.backend.service;

import com.chatmosphere.backend.documents.Message;
import com.chatmosphere.backend.documents.Room;
import com.chatmosphere.backend.dto.MessageDTO;
import com.chatmosphere.backend.dto.RoomDTO;
import com.chatmosphere.backend.repository.MessageRepository;
import com.chatmosphere.backend.repository.RoomsRepository;
import com.chatmosphere.backend.vo.CreateRoomRequestVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RoomsService {

    private final RoomsRepository roomsRepository;
    private final MessageRepository messageRepository;

    public RoomDTO createRoom(CreateRoomRequestVO requestVO) {
        String roomId = requestVO.getRoomId();
        Optional<Room> roomById = findRoomById(roomId);
        if (roomById.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room with Id " + roomId + " already exists");
        }

        Room newRoom = new Room();
        newRoom.setRoomId(roomId);
        newRoom.setRoomName(requestVO.getRoomName());
        newRoom.setCreateDate(LocalDateTime.now(ZoneOffset.UTC));
        Room savedRoom = saveRoom(newRoom);
        return mapToRoomDTO(savedRoom);
    }

    public Optional<Room> findRoomById(String roomId) {
        return roomsRepository.findByRoomId(roomId);
    }
    public Room findRoomByIdOrElseThrow(String roomId) {
        return roomsRepository.findByRoomId(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found with Id " + roomId));
    }

    public Room saveRoom(Room room) {
        return roomsRepository.save(room);
    }

    public Map<String, Object> getMessagesByRoomId(String roomId, int pageNo, int pageSize) {
        findRoomByIdOrElseThrow(roomId);

        Pageable pageable = PageRequest.of(pageNo, pageSize);
        Page<Message> messagePage = messageRepository.findByRoomIdOrderBySentAtDesc(roomId, pageable);

        List<Message> messages = new ArrayList<>(messagePage.getContent());
        Collections.reverse(messages);

        List<MessageDTO> messageDTOs = messages.stream()
                .map(this::mapToMessageDTO)
                .toList();

        return Map.of("messages", messageDTOs, "totalMessages", messagePage.getTotalElements());
    }

    public RoomDTO mapToRoomDTO(Room room) {
        if (room == null) return null;
        RoomDTO dto = new RoomDTO();
        dto.setId(room.getId());
        dto.setRoomId(room.getRoomId());
        dto.setRoomName(room.getRoomName());
        dto.setCreatedBy(room.getCreatedBy());
        dto.setCreateDate(room.getCreateDate());
        dto.setUpdatedBy(room.getUpdatedBy());
        dto.setUpdateDate(room.getUpdateDate());
        return dto;
    }

    public MessageDTO mapToMessageDTO(Message message) {
        if (message == null) return null;
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setRoomId(message.getRoomId());
        dto.setSenderId(message.getSenderId());
        dto.setContent(message.getContent());
        dto.setSentAt(message.getSentAt());
        return dto;
    }
}
