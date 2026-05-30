package com.chatmosphere.backend.repository;

import com.chatmosphere.backend.entity.RoomMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomMetaRepository extends JpaRepository<RoomMeta, Long> {
    Optional<RoomMeta> findByRoomId(String roomId);
    List<RoomMeta> findByParticipantsUsername(String username);

    @Query("SELECT DISTINCT r FROM RoomMeta r JOIN r.participants p WHERE p.username = :username AND (" +
           "  (r.isGroup = true AND LOWER(r.roomName) LIKE LOWER(CONCAT('%', :query, '%'))) OR " +
           "  (r.isGroup = false AND EXISTS (" +
           "    SELECT p2 FROM r.participants p2 WHERE p2.username <> :username AND (" +
           "      LOWER(p2.displayName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "      LOWER(p2.mobileNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "      EXISTS (" +
           "        SELECT c FROM Contact c WHERE c.user.username = :username " +
           "        AND c.contactUser.username = p2.username AND LOWER(c.contactName) LIKE LOWER(CONCAT('%', :query, '%'))" +
           "      )" +
           "    )" +
           "  ))" +
           ")")
    List<RoomMeta> searchRoomsForUser(@Param("username") String username, @Param("query") String query);
}
