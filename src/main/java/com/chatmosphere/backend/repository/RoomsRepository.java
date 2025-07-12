package com.chatmosphere.backend.repository;

import com.chatmosphere.backend.documents.Room;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomsRepository extends MongoRepository<Room, String> {

    Optional<Room> findByRoomId(String roomId);
}
