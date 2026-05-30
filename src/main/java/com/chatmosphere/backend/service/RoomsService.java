package com.chatmosphere.backend.service;

import com.chatmosphere.backend.documents.Message;
import com.chatmosphere.backend.documents.Room;
import com.chatmosphere.backend.dto.MessageDTO;
import com.chatmosphere.backend.dto.RoomDTO;
import com.chatmosphere.backend.dto.UserDTO;
import com.chatmosphere.backend.entity.Contact;
import com.chatmosphere.backend.entity.RoomMeta;
import com.chatmosphere.backend.entity.User;
import com.chatmosphere.backend.repository.ContactRepository;
import com.chatmosphere.backend.repository.MessageRepository;
import com.chatmosphere.backend.repository.RoomMetaRepository;
import com.chatmosphere.backend.repository.RoomsRepository;
import com.chatmosphere.backend.repository.UserRepository;
import com.chatmosphere.backend.vo.CreateRoomRequestVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RoomsService {

    private final RoomsRepository roomsRepository;
    private final RoomMetaRepository roomMetaRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ContactRepository contactRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // =========================================================================
    // Core Business Methods
    // =========================================================================

    /**
     * Creates a new group chat room and its associated metadata.
     */
    public RoomDTO createRoom(CreateRoomRequestVO requestVO, String creatorUsername) {
        String roomId = requestVO.getRoomId();
        if (findRoomById(roomId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room with Id " + roomId + " already exists");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Set<String> participantUsernames = resolveParticipantUsernames(creatorUsername, requestVO.getParticipantUsernames());

        createAndSaveMongoRoom(roomId, requestVO.getRoomName(), creatorUsername, participantUsernames, now);
        RoomMeta roomMeta = createAndSaveSqlRoomMeta(roomId, requestVO.getRoomName(), creatorUsername, participantUsernames, now);

        return mapToRoomDTO(roomMeta, creatorUsername);
    }

    /**
     * Retrieves or creates a deterministic personal DM room between two users.
     */
    public RoomDTO getOrCreatePersonalRoom(String currentUser, String recipientUsername) {
        if (currentUser.equals(recipientUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot start a personal chat with yourself");
        }

        User recipient = userRepository.findByUsername(recipientUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient user not found: " + recipientUsername));

        // Deterministic roomId format: dm_user1_user2 sorted alphabetically
        String roomId = currentUser.compareTo(recipientUsername) < 0
                ? "dm_" + currentUser + "_" + recipientUsername
                : "dm_" + recipientUsername + "_" + currentUser;

        Optional<RoomMeta> existingRoomMeta = roomMetaRepository.findByRoomId(roomId);
        if (existingRoomMeta.isPresent()) {
            return mapToRoomDTO(existingRoomMeta.get(), currentUser);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createAndSaveMongoPersonalRoom(roomId, currentUser, recipientUsername, now);
        RoomMeta roomMeta = createAndSaveSqlPersonalRoomMeta(roomId, currentUser, recipient, now);

        return mapToRoomDTO(roomMeta, currentUser);
    }

    /**
     * Invites a set of users into an existing group room.
     * Only current participants may invite; DM rooms are not supported.
     */
    public RoomDTO inviteUsersToRoom(String roomId, Set<String> usernames, String requesterUsername) {
        Room room = findRoomByIdOrElseThrow(roomId);

        if (!room.isGroup()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot invite users into a direct message room");
        }

        if (room.getParticipants() == null || !room.getParticipants().contains(requesterUsername)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a participant of this room");
        }

        RoomMeta roomMeta = roomMetaRepository.findByRoomId(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room metadata not found"));

        List<String> addedUsers = new ArrayList<>();
        for (String username : usernames) {
            if (username == null || username.isBlank()) continue;
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) continue;

            User user = userOpt.get();
            // Skip users already in the room
            if (room.getParticipants().contains(username)) continue;

            // Update Mongo room
            room.getParticipants().add(username);

            // Update SQL RoomMeta
            if (roomMeta.getParticipants() == null) {
                roomMeta.setParticipants(new HashSet<>());
            }
            roomMeta.getParticipants().add(user);
            addedUsers.add(username);
        }

        if (!addedUsers.isEmpty()) {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

            // Update activity timestamps and save
            updateRoomAndMetaActivity(room, roomMeta, now);

            // Broadcast a system-style invite notification over WebSocket
            String inviteMsg = requesterUsername + " invited " + String.join(", ", addedUsers) + " to the space";

            Message message = new Message();
            message.setRoomId(roomId);
            message.setSenderId("system");
            message.setContent(inviteMsg);
            message.setSentAt(now);
            Message savedMessage = messageRepository.save(message);

            messagingTemplate.convertAndSend("/topic/room/" + roomId, mapToMessageDTO(savedMessage));
        }

        return mapToRoomDTO(roomMeta, requesterUsername);
    }

    /**
     * Removes a participant from an existing group room.
     * Only the creator can remove members; participants can remove themselves to leave.
     */
    public RoomDTO removeUserFromRoom(String roomId, String usernameToRemove, String requesterUsername) {
        Room room = findRoomByIdOrElseThrow(roomId);

        if (!room.isGroup()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove users from a direct message room");
        }

        if (room.getParticipants() == null || !room.getParticipants().contains(usernameToRemove)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a participant of this room");
        }

        RoomMeta roomMeta = roomMetaRepository.findByRoomId(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room metadata not found"));

        String creator = roomMeta.getCreatedBy();
        boolean isCreator = requesterUsername.equals(creator);
        boolean isSelfRemove = requesterUsername.equals(usernameToRemove);

        if (!isCreator && !isSelfRemove) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to remove this participant");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // Update Mongo room
        room.getParticipants().remove(usernameToRemove);

        // Update SQL RoomMeta
        if (roomMeta.getParticipants() != null) {
            roomMeta.getParticipants().removeIf(user -> user.getUsername().equals(usernameToRemove));
        }

        updateRoomAndMetaActivity(room, roomMeta, now);

        // Broadcast a system-style remove notification over WebSocket
        String removeMsg = isSelfRemove
                ? usernameToRemove + " left the space"
                : requesterUsername + " removed " + usernameToRemove + " from the space";

        Message message = new Message();
        message.setRoomId(roomId);
        message.setSenderId("system");
        message.setContent(removeMsg);
        message.setSentAt(now);
        Message savedMessage = messageRepository.save(message);

        messagingTemplate.convertAndSend("/topic/room/" + roomId, mapToMessageDTO(savedMessage));

        return mapToRoomDTO(roomMeta, requesterUsername);
    }

    /**
     * Lists all rooms active for the user, with support for search filters.
     */
    public List<RoomDTO> getAllRoomsForUser(String username, String searchQuery) {
        List<RoomMeta> personalRooms = StringUtils.hasText(searchQuery)
                ? roomMetaRepository.searchRoomsForUser(username, searchQuery.trim())
                : roomMetaRepository.findByParticipantsUsername(username);

        // Fetch contacts in a single query to eliminate N+1 database hits
        List<Contact> contacts = contactRepository.findByUserUsername(username);
        Map<String, String> contactAliasMap = new HashMap<>();
        if (contacts != null) {
            for (Contact c : contacts) {
                if (c.getContactUser() != null) {
                    contactAliasMap.put(c.getContactUser().getUsername(), c.getContactName());
                }
            }
        }

        List<RoomDTO> roomDTOs = new ArrayList<>();
        for (RoomMeta r : personalRooms) {
            roomDTOs.add(mapToRoomDTO(r, username, contactAliasMap));
        }

        // Sort by updateDate (fallback to createDate) descending
        roomDTOs.sort((r1, r2) -> {
            LocalDateTime t1 = r1.getUpdateDate() != null ? r1.getUpdateDate() : r1.getCreateDate();
            LocalDateTime t2 = r2.getUpdateDate() != null ? r2.getUpdateDate() : r2.getCreateDate();
            if (t1 == null && t2 == null) return 0;
            if (t1 == null) return 1;
            if (t2 == null) return -1;
            return t2.compareTo(t1);
        });

        return roomDTOs;
    }

    /**
     * Returns paginated messages for a given room.
     */
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

    // =========================================================================
    // Database Lookup / Persistence Methods
    // =========================================================================

    public Optional<Room> findRoomById(String roomId) {
        return roomsRepository.findByRoomId(roomId);
    }

    public Room findRoomByIdOrElseThrow(String roomId) {
        return roomsRepository.findByRoomId(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found with Id " + roomId));
    }

    public Optional<RoomMeta> findRoomMetaByRoomId(String roomId) {
        return roomMetaRepository.findByRoomId(roomId);
    }

    public void saveRoom(Room room) {
        roomsRepository.save(room);
    }

    public void saveRoomMeta(RoomMeta roomMeta) {
        roomMetaRepository.save(roomMeta);
    }

    public void updateRoomAndMetaActivity(Room room, LocalDateTime updateTime) {
        room.setUpdateDate(updateTime);
        roomsRepository.save(room);

        findRoomMetaByRoomId(room.getRoomId()).ifPresent(roomMeta -> {
            roomMeta.setUpdateDate(updateTime);
            roomMetaRepository.save(roomMeta);
        });
    }

    public void updateRoomAndMetaActivity(Room room, RoomMeta roomMeta, LocalDateTime updateTime) {
        room.setUpdateDate(updateTime);
        roomsRepository.save(room);

        if (roomMeta != null) {
            roomMeta.setUpdateDate(updateTime);
            roomMetaRepository.save(roomMeta);
        }
    }

    // =========================================================================
    // Mapping & DTO Conversion Helpers
    // =========================================================================

    public RoomDTO mapToRoomDTO(RoomMeta roomMeta, String currentUsername) {
        return mapToRoomDTO(roomMeta, currentUsername, null);
    }

    public RoomDTO mapToRoomDTO(RoomMeta roomMeta, String currentUsername, Map<String, String> contactAliasMap) {
        if (roomMeta == null) return null;

        RoomDTO dto = new RoomDTO();
        dto.setId(String.valueOf(roomMeta.getId()));
        dto.setRoomId(roomMeta.getRoomId());
        dto.setRoomName(roomMeta.getRoomName());
        dto.setCreatedBy(roomMeta.getCreatedBy());
        dto.setCreateDate(roomMeta.getCreateDate());
        dto.setUpdatedBy(roomMeta.getUpdatedBy());
        dto.setUpdateDate(roomMeta.getUpdateDate());
        dto.setGroup(roomMeta.isGroup());

        Set<String> participantUsernames = new HashSet<>();
        if (roomMeta.getParticipants() != null) {
            for (User u : roomMeta.getParticipants()) {
                participantUsernames.add(u.getUsername());
            }
        }
        dto.setParticipants(participantUsernames);

        enrichDMRoomRecipient(dto, roomMeta, currentUsername, contactAliasMap);

        return dto;
    }

    public RoomDTO mapToRoomDTO(Room room, String currentUsername) {
        if (room == null) return null;

        RoomDTO dto = new RoomDTO();
        dto.setId(room.getId());
        dto.setRoomId(room.getRoomId());
        dto.setRoomName(room.getRoomName());
        String creator = room.getCreatedBy();
        if (creator == null || creator.isBlank()) {
            Optional<RoomMeta> metaOpt = roomMetaRepository.findByRoomId(room.getRoomId());
            if (metaOpt.isPresent()) {
                creator = metaOpt.get().getCreatedBy();
            }
        }
        dto.setCreatedBy(creator);
        dto.setCreateDate(room.getCreateDate());
        dto.setUpdatedBy(room.getUpdatedBy());
        dto.setUpdateDate(room.getUpdateDate());
        dto.setGroup(room.isGroup());
        dto.setParticipants(room.getParticipants());

        enrichDMRoomRecipient(dto, room, currentUsername);

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

    // =========================================================================
    // Modular Sub-Methods
    // =========================================================================

    private Set<String> resolveParticipantUsernames(String creatorUsername, Set<String> requestedUsernames) {
        Set<String> participantUsernames = new HashSet<>();
        participantUsernames.add(creatorUsername);
        if (requestedUsernames != null) {
            for (String pUsername : requestedUsernames) {
                if (userRepository.findByUsername(pUsername).isPresent()) {
                    participantUsernames.add(pUsername);
                }
            }
        }
        return participantUsernames;
    }

    private void createAndSaveMongoRoom(String roomId, String roomName, String creatorUsername, Set<String> participantUsernames, LocalDateTime now) {
        Room room = new Room();
        room.setRoomId(roomId);
        room.setRoomName(roomName);
        room.setGroup(true);
        room.setParticipants(participantUsernames);
        room.setCreatedBy(creatorUsername);
        room.setCreateDate(now);
        room.setUpdateDate(now);
        roomsRepository.save(room);
    }

    private RoomMeta createAndSaveSqlRoomMeta(String roomId, String roomName, String creatorUsername, Set<String> participantUsernames, LocalDateTime now) {
        RoomMeta roomMeta = new RoomMeta();
        roomMeta.setRoomId(roomId);
        roomMeta.setRoomName(roomName);
        roomMeta.setGroup(true);
        roomMeta.setCreatedBy(creatorUsername);
        roomMeta.setCreateDate(now);
        roomMeta.setUpdateDate(now);

        Set<User> participants = new HashSet<>();
        for (String pUsername : participantUsernames) {
            userRepository.findByUsername(pUsername).ifPresent(participants::add);
        }
        roomMeta.setParticipants(participants);
        return roomMetaRepository.save(roomMeta);
    }

    private void createAndSaveMongoPersonalRoom(String roomId, String currentUser, String recipientUsername, LocalDateTime now) {
        Room room = new Room();
        room.setRoomId(roomId);
        room.setRoomName("Direct Message");
        room.setGroup(false);
        room.setParticipants(new HashSet<>(Arrays.asList(currentUser, recipientUsername)));
        room.setCreateDate(now);
        room.setCreatedBy(currentUser);
        room.setUpdateDate(now);
        roomsRepository.save(room);
    }

    private RoomMeta createAndSaveSqlPersonalRoomMeta(String roomId, String currentUser, User recipient, LocalDateTime now) {
        RoomMeta roomMeta = new RoomMeta();
        roomMeta.setRoomId(roomId);
        roomMeta.setRoomName("Direct Message");
        roomMeta.setGroup(false);
        roomMeta.setCreatedBy(currentUser);
        roomMeta.setCreateDate(now);
        roomMeta.setUpdateDate(now);

        Set<User> participants = new HashSet<>();
        userRepository.findByUsername(currentUser).ifPresent(participants::add);
        participants.add(recipient);
        roomMeta.setParticipants(participants);
        return roomMetaRepository.save(roomMeta);
    }

    private void enrichDMRoomRecipient(RoomDTO dto, RoomMeta roomMeta, String currentUsername, Map<String, String> contactAliasMap) {
        if (roomMeta.isGroup() || roomMeta.getParticipants() == null || currentUsername == null) {
            return;
        }

        User recipientUser = roomMeta.getParticipants().stream()
                .filter(u -> !u.getUsername().equals(currentUsername))
                .findFirst()
                .orElse(null);

        if (recipientUser != null) {
            UserDTO userDTO = buildRecipientUserDTO(recipientUser);
            dto.setRecipient(userDTO);

            if (contactAliasMap != null) {
                String alias = contactAliasMap.get(recipientUser.getUsername());
                if (alias != null) {
                    dto.setRoomName(alias);
                    userDTO.setDisplayName(alias);
                }
            } else {
                contactRepository.findByUserUsernameAndContactUserUsername(currentUsername, recipientUser.getUsername())
                        .ifPresent(contact -> {
                            dto.setRoomName(contact.getContactName());
                            userDTO.setDisplayName(contact.getContactName());
                        });
            }
        }
    }

    private void enrichDMRoomRecipient(RoomDTO dto, Room room, String currentUsername) {
        if (room.isGroup() || room.getParticipants() == null || currentUsername == null) {
            return;
        }

        String recipientUsername = room.getParticipants().stream()
                .filter(u -> !u.equals(currentUsername))
                .findFirst()
                .orElse(currentUsername);

        userRepository.findByUsername(recipientUsername).ifPresent(user -> {
            UserDTO userDTO = buildRecipientUserDTO(user);
            dto.setRecipient(userDTO);

            contactRepository.findByUserUsernameAndContactUserUsername(currentUsername, recipientUsername)
                    .ifPresent(contact -> {
                        dto.setRoomName(contact.getContactName());
                        userDTO.setDisplayName(contact.getContactName());
                    });
        });
    }

    private UserDTO buildRecipientUserDTO(User user) {
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(user.getUsername());
        userDTO.setDisplayName(user.getDisplayName());
        userDTO.setAvatarUrl(user.getAvatarUrl());
        userDTO.setMobileNumber(user.getMobileNumber());
        userDTO.setEmail(user.getEmail());
        return userDTO;
    }
}
