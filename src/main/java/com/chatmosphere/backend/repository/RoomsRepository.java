package com.chatmosphere.backend.repository;

import com.chatmosphere.backend.documents.Room;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.mongodb.repository.Query;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomsRepository extends MongoRepository<Room, String> {

    Optional<Room> findByRoomId(String roomId);

    @Query("{ 'participants': ?0 }")
    List<Room> findByParticipantsContaining(String username);

    @Query("{ '$or': [ { 'isGroup': true }, { 'isGroup': { '$exists': false } } ] }")
    List<Room> findAllGroupRooms();
}
