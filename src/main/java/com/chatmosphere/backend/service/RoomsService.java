package com.chatmosphere.backend.service;

import com.chatmosphere.backend.documents.Message;
import com.chatmosphere.backend.documents.Room;
import com.chatmosphere.backend.repository.RoomsRepository;
import com.chatmosphere.backend.vo.CreateRoomRequestVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RoomsService {

    private final RoomsRepository roomsRepository;

    public Room createRoom(CreateRoomRequestVO requestVO) {
        String roomId = requestVO.getRoomId();
        Optional<Room> roomById = findRoomById(roomId);
        if (roomById.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room with Id " + roomId + " already exists");
        }

        Room newRoom = new Room();
        newRoom.setRoomId(roomId);
        newRoom.setRoomName(requestVO.getRoomName());
        newRoom.setCreateDate(LocalDateTime.now());
        return roomsRepository.save(newRoom);
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

    public List<Message> getMessagesByRoomId(String roomId, int pageNo, int pageSize) {
        Room room = findRoomByIdOrElseThrow(roomId);

        // Message Pagination
        List<Message> messages = room.getMessages();
        int start = Math.max(0, messages.size() - (pageNo + 1) * pageSize);
        int end = Math.min(messages.size(), start + pageSize);

        return messages.subList(start, end);
    }
}
