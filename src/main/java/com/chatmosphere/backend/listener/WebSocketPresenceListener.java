package com.chatmosphere.backend.listener;

import com.chatmosphere.backend.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class WebSocketPresenceListener {

    private final PresenceService presenceService;

    // Track mapping of sessionId_subscriptionId -> roomId to handle unsubscribing
    private final Map<String, String> subscriptionToRoomMap = new ConcurrentHashMap<>();

    @EventListener
    public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        if (destination != null && destination.startsWith("/topic/room/")
                && !destination.endsWith("/presence") && !destination.endsWith("/typing")) {
            String roomId = destination.replace("/topic/room/", "");
            String username = accessor.getUser() != null ? accessor.getUser().getName() : null;
            if (username != null) {
                String subKey = accessor.getSessionId() + "_" + accessor.getSubscriptionId();
                subscriptionToRoomMap.put(subKey, roomId);
                presenceService.userJoined(roomId, username);
            }
        }
    }

    @EventListener
    public void handleSessionUnsubscribeEvent(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String subKey = accessor.getSessionId() + "_" + accessor.getSubscriptionId();
        String roomId = subscriptionToRoomMap.remove(subKey);
        String username = accessor.getUser() != null ? accessor.getUser().getName() : null;
        if (roomId != null && username != null) {
            presenceService.userLeft(roomId, username);
        }
    }

    @EventListener
    public void handleSessionDisconnectEvent(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = accessor.getUser() != null ? accessor.getUser().getName() : null;
        if (username != null) {
            presenceService.removeUserFromAllRooms(username);
            // Clean up session mappings
            subscriptionToRoomMap.entrySet().removeIf(entry -> entry.getKey().startsWith(accessor.getSessionId() + "_"));
        }
    }
}
