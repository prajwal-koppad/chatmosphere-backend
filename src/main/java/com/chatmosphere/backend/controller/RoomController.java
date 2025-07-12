package com.chatmosphere.backend.controller;

import com.chatmosphere.backend.documents.Message;
import com.chatmosphere.backend.documents.Room;
import com.chatmosphere.backend.service.RoomsService;
import com.chatmosphere.backend.vo.CreateRoomRequestVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/rooms/")
@CrossOrigin("https://localhost:3000")
@RequiredArgsConstructor
public class RoomController {

    private final RoomsService roomsService;

    @PostMapping("create-room")
    public ResponseEntity<Room> createRoom(@RequestBody CreateRoomRequestVO createRoomRequestVO) {
        Room room = roomsService.createRoom(createRoomRequestVO);
        return new ResponseEntity<>(room, HttpStatus.CREATED);
    }

    @GetMapping("{roomId}")
    public ResponseEntity<?> getRoomById(@PathVariable(name = "roomId") String roomId) {
        Room room = roomsService.findRoomById(roomId);
        return new ResponseEntity<>(room, HttpStatus.OK);
    }

    @GetMapping("{roomId}/messages")
    public ResponseEntity<List<Message>> getMessagesById(@PathVariable(name = "roomId") String roomId,
                                                       @RequestParam(name = "pageNo", defaultValue = "0", required = false) int pageNumber,
                                                       @RequestParam(name = "pageSize", defaultValue = "20", required = false) int pageSize) {
        List<Message> messages = roomsService.getMessagesByRoomId(roomId, pageNumber, pageSize);
        return new ResponseEntity<>(messages, HttpStatus.OK);
    }
}
