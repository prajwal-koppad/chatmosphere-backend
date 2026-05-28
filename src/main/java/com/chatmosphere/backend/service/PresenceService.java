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

    // Map of roomId -> Set of active usernames
    private final Map<String, Set<String>> roomUsers = new ConcurrentHashMap<>();

    public void userJoined(String roomId, String username) {
        roomUsers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(username);
        broadcastPresence(roomId);
    }

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

    public void removeUserFromAllRooms(String username) {
        roomUsers.forEach((roomId, users) -> {
            if (users.contains(username)) {
                userLeft(roomId, username);
            }
        });
    }

    public Set<String> getOnlineUsers(String roomId) {
        return roomUsers.getOrDefault(roomId, Collections.emptySet());
    }

    private void broadcastPresence(String roomId) {
        Set<String> onlineUsers = getOnlineUsers(roomId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/presence", onlineUsers);
    }
}
