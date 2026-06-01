package com.chatmosphere.backend.scheduler;

import com.chatmosphere.backend.documents.Room;
import com.chatmosphere.backend.entity.PendingVerification;
import com.chatmosphere.backend.repository.PendingVerificationRepository;
import com.chatmosphere.backend.repository.RoomsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * DbKeepAliveScheduler
 *
 * Runs every 3 hours to keep both the MySQL and MongoDB connections
 * active on cloud/free-tier environments (e.g. Hugging Face Spaces,
 * Railway, Render) that may hibernate idle datasource connections.
 *
 * Strategy per database:
 *  - MySQL  : insert a sentinel row into pending_verifications, fetch it,
 *             then delete it — all inside the same scheduled tick.
 *  - MongoDB: insert a sentinel Room document, fetch it back by roomId,
 *             then delete it — same pattern.
 *
 * The sentinel records are immediately deleted so they never pollute
 * real application data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbKeepAliveScheduler {

    private static final String SENTINEL_PREFIX  = "__keepalive__";
    private static final String SQL_SENTINEL_EMAIL  = SENTINEL_PREFIX + "@chatmosphere.internal";
    private static final String SQL_SENTINEL_TOKEN  = SENTINEL_PREFIX + "sql_token";
    private static final String MONGO_SENTINEL_ROOM = SENTINEL_PREFIX + "room";

    private final PendingVerificationRepository pendingVerificationRepository;
    private final RoomsRepository roomsRepository;

    /**
     * Fires every 3 hours (10 800 000 ms).
     * An initial delay of 2 minutes is applied so the scheduler does not
     * run before the application context is fully ready after a cold start.
     */
    @Scheduled(fixedDelay = 10_800_000, initialDelay = 120_000)
    public void keepAlive() {
        log.info("[KeepAlive] Starting scheduled DB keep-alive ping — {}", LocalDateTime.now());
        pingMySQL();
        pingMongoDB();
        log.info("[KeepAlive] Keep-alive ping complete — {}", LocalDateTime.now());
    }

    // ---------------------------------------------------------------
    // MySQL keep-alive
    // ---------------------------------------------------------------

    private void pingMySQL() {
        try {
            // 1. Clean up any leftover sentinel from a previous failed run
            pendingVerificationRepository.findByEmail(SQL_SENTINEL_EMAIL)
                    .ifPresent(pendingVerificationRepository::delete);

            // 2. Save a fresh sentinel record
            PendingVerification sentinel = new PendingVerification();
            sentinel.setEmail(SQL_SENTINEL_EMAIL);
            sentinel.setToken(SQL_SENTINEL_TOKEN);
            sentinel.setVerified(false);
            sentinel.setExpiryTime(LocalDateTime.now().plusMinutes(5));
            pendingVerificationRepository.save(sentinel);
            log.debug("[KeepAlive][MySQL] Sentinel row saved — email={}", SQL_SENTINEL_EMAIL);

            // 3. Fetch it back to confirm read path works
            Optional<PendingVerification> fetched = pendingVerificationRepository.findByEmail(SQL_SENTINEL_EMAIL);
            if (fetched.isPresent()) {
                log.debug("[KeepAlive][MySQL] Sentinel row fetched OK — id={}", fetched.get().getId());
            } else {
                log.warn("[KeepAlive][MySQL] Sentinel row not found after save — possible issue!");
            }

            // 4. Delete it immediately
            fetched.ifPresent(pendingVerificationRepository::delete);
            log.info("[KeepAlive][MySQL] Ping successful ✓");

        } catch (Exception e) {
            log.error("[KeepAlive][MySQL] Ping failed: {}", e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------
    // MongoDB keep-alive
    // ---------------------------------------------------------------

    private void pingMongoDB() {
        try {
            // 1. Clean up any leftover sentinel from a previous failed run
            roomsRepository.findByRoomId(MONGO_SENTINEL_ROOM)
                    .ifPresent(roomsRepository::delete);

            // 2. Save a fresh sentinel document
            Room sentinel = new Room();
            sentinel.setRoomId(MONGO_SENTINEL_ROOM);
            sentinel.setRoomName("Keep-Alive Sentinel");
            sentinel.setGroup(false);
            sentinel.setParticipants(Set.of(SENTINEL_PREFIX));
            sentinel.setCreateDate(LocalDateTime.now());
            sentinel.setCreatedBy(SENTINEL_PREFIX);
            roomsRepository.save(sentinel);
            log.debug("[KeepAlive][MongoDB] Sentinel document saved — roomId={}", MONGO_SENTINEL_ROOM);

            // 3. Fetch it back to confirm read path works
            Optional<Room> fetched = roomsRepository.findByRoomId(MONGO_SENTINEL_ROOM);
            if (fetched.isPresent()) {
                log.debug("[KeepAlive][MongoDB] Sentinel document fetched OK — id={}", fetched.get().getId());
            } else {
                log.warn("[KeepAlive][MongoDB] Sentinel document not found after save — possible issue!");
            }

            // 4. Delete it immediately
            fetched.ifPresent(roomsRepository::delete);
            log.info("[KeepAlive][MongoDB] Ping successful ✓");

        } catch (Exception e) {
            log.error("[KeepAlive][MongoDB] Ping failed: {}", e.getMessage(), e);
        }
    }
}
