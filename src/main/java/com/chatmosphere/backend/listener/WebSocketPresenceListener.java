package com.chatmosphere.backend.listener;

import com.chatmosphere.backend.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class WebSocketPresenceListener {

    private final PresenceService presenceService;

    // Track mapping of sessionId_subscriptionId -> roomId to handle unsubscribing
    private final Map<String, String> subscriptionToRoomMap = new ConcurrentHashMap<>();

    // Track mapping of sessionId -> username to handle disconnects/unsubscribes where principal is null
    private final Map<String, String> sessionToUserMap = new ConcurrentHashMap<>();

    @EventListener
    public void handleSessionSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();
        
        log.info("WebSocket Subscribe Event: destination={}, session={}, user={}", 
                 destination, sessionId, event.getUser());

        if (destination != null && destination.startsWith("/topic/room/")) {
            String roomId;
            boolean isPresenceTopic = destination.endsWith("/presence");
            boolean isTypingTopic = destination.endsWith("/typing");
            
            if (isTypingTopic) {
                return; // Ignore typing topics for presence tracking
            }
            
            if (isPresenceTopic) {
                roomId = destination.substring("/topic/room/".length(), destination.length() - "/presence".length());
            } else {
                roomId = destination.substring("/topic/room/".length());
            }

            String username = event.getUser() != null ? event.getUser().getName() : null;
            if (username != null) {
                sessionToUserMap.put(sessionId, username);
                String subKey = sessionId + "_" + accessor.getSubscriptionId();
                subscriptionToRoomMap.put(subKey, roomId);
                
                log.info("User {} joined room {} (session={}, subKey={})", username, roomId, sessionId, subKey);
                presenceService.userJoined(roomId, username);
            } else {
                log.warn("Subscribe event user principal is null for destination: {}", destination);
            }
        }
    }

    @EventListener
    public void handleSessionUnsubscribeEvent(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String subKey = sessionId + "_" + accessor.getSubscriptionId();
        String roomId = subscriptionToRoomMap.remove(subKey);
        
        String username = event.getUser() != null ? event.getUser().getName() : null;
        if (username == null) {
            username = sessionToUserMap.get(sessionId);
        }

        log.info("WebSocket Unsubscribe Event: subKey={}, room={}, user={}", subKey, roomId, username);

        if (roomId != null && username != null) {
            // Check if there are other subscriptions for this sessionId and roomId
            boolean hasOtherSubscriptions = subscriptionToRoomMap.entrySet().stream()
                    .anyMatch(entry -> entry.getKey().startsWith(sessionId + "_") && entry.getValue().equals(roomId));
            
            if (!hasOtherSubscriptions) {
                log.info("User {} left room {} (no more active subscriptions in session {})", username, roomId, sessionId);
                presenceService.userLeft(roomId, username);
            } else {
                log.info("User {} unsubscribed from {} in session {}, but has other active subscriptions to this room", 
                         username, subKey, sessionId);
            }
        }
    }

    @EventListener
    public void handleSessionDisconnectEvent(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        
        String username = event.getUser() != null ? event.getUser().getName() : null;
        if (username == null) {
            username = sessionToUserMap.remove(sessionId);
        } else {
            sessionToUserMap.remove(sessionId);
        }

        log.info("WebSocket Disconnect Event: session={}, user={}", sessionId, username);

        if (username != null) {
            presenceService.removeUserFromAllRooms(username);
            // Clean up session mappings
            subscriptionToRoomMap.entrySet().removeIf(entry -> entry.getKey().startsWith(sessionId + "_"));
        }
    }
}
