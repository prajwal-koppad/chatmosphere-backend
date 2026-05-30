package com.chatmosphere.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private final SimpMessagingTemplate messagingTemplate;

    // In-memory registry mapping: roomId -> Set of active usernames in that room
    private final Map<String, Set<String>> roomUsers = new ConcurrentHashMap<>();

    // =========================================================================
    // Public Operations
    // =========================================================================

    /**
     * Adds a user to the online list of a room and broadcasts the update.
     */
    public void userJoined(String roomId, String username) {
        roomUsers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(username);
        broadcastPresence(roomId);
    }

    /**
     * Removes a user from the online list of a room and broadcasts the update.
     */
    public void userLeft(String roomId, String username) {
        Set<String> users = roomUsers.get(roomId);
        if (users != null) {
            users.remove(username);
            if (users.isEmpty()) {
                roomUsers.remove(roomId);
            }
        }
        broadcastPresence(roomId);
    }

    /**
     * Iterates all rooms and removes a disconnected user.
     */
    public void removeUserFromAllRooms(String username) {
        roomUsers.forEach((roomId, users) -> {
            if (users.contains(username)) {
                userLeft(roomId, username);
            }
        });
    }

    /**
     * Retrieves the set of currently online users in a room.
     */
    public Set<String> getOnlineUsers(String roomId) {
        return roomUsers.getOrDefault(roomId, Collections.emptySet());
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    private void broadcastPresence(String roomId) {
        Set<String> onlineUsers = getOnlineUsers(roomId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/presence", onlineUsers);
    }
}
