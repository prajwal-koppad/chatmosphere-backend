package com.chatmosphere.backend.controller;

import com.chatmosphere.backend.dto.RoomDTO;
import com.chatmosphere.backend.documents.Room;
import com.chatmosphere.backend.service.RoomsService;
import com.chatmosphere.backend.vo.CreateRoomRequestVO;
import com.chatmosphere.backend.vo.CreatePersonalRoomRequestVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/v1/rooms/")
@RequiredArgsConstructor
public class RoomController {

    private final RoomsService roomsService;

    @PostMapping("create-room")
    public ResponseEntity<RoomDTO> createRoom(@Valid @RequestBody CreateRoomRequestVO createRoomRequestVO, Principal principal) {
        RoomDTO room = roomsService.createRoom(createRoomRequestVO, principal.getName());
        return new ResponseEntity<>(room, HttpStatus.CREATED);
    }

    @PostMapping("personal")
    public ResponseEntity<RoomDTO> createPersonalRoom(@Valid @RequestBody CreatePersonalRoomRequestVO personalRoomRequest, Principal principal) {
        RoomDTO room = roomsService.getOrCreatePersonalRoom(principal.getName(), personalRoomRequest.getRecipientUsername());
        return new ResponseEntity<>(room, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<RoomDTO>> getRooms(@RequestParam(name = "search", required = false) String search, Principal principal) {
        List<RoomDTO> rooms = roomsService.getAllRoomsForUser(principal.getName(), search);
        return ResponseEntity.ok(rooms);
    }

    @GetMapping("{roomId}")
    public ResponseEntity<RoomDTO> getRoomById(@PathVariable(name = "roomId") String roomId, Principal principal) {
        Room room = roomsService.findRoomByIdOrElseThrow(roomId);
        RoomDTO roomDTO = roomsService.mapToRoomDTO(room, principal.getName());
        return new ResponseEntity<>(roomDTO, HttpStatus.OK);
    }

    @GetMapping("{roomId}/messages")
    public ResponseEntity<Map<String, Object>> getMessagesById(@PathVariable(name = "roomId") String roomId,
                                                               @RequestParam(name = "pageNo", defaultValue = "0", required = false) int pageNumber,
                                                               @RequestParam(name = "pageSize", defaultValue = "20", required = false) int pageSize) {
        Map<String, Object> messages = roomsService.getMessagesByRoomId(roomId, pageNumber, pageSize);
        return new ResponseEntity<>(messages, HttpStatus.OK);
    }

    @PatchMapping("{roomId}/invite")
    public ResponseEntity<RoomDTO> inviteUsers(@PathVariable(name = "roomId") String roomId,
                                               @Valid @RequestBody com.chatmosphere.backend.vo.InviteUsersRequestVO request,
                                               Principal principal) {
        RoomDTO updated = roomsService.inviteUsersToRoom(roomId, request.getUsernames(), principal.getName());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("{roomId}/participants/{username}")
    public ResponseEntity<RoomDTO> removeParticipant(@PathVariable(name = "roomId") String roomId,
                                                     @PathVariable(name = "username") String username,
                                                     Principal principal) {
        RoomDTO updated = roomsService.removeUserFromRoom(roomId, username, principal.getName());
        return ResponseEntity.ok(updated);
    }
}
