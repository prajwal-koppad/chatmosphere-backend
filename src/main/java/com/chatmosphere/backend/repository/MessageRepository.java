package com.chatmosphere.backend.repository;

import com.chatmosphere.backend.documents.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {
    Page<Message> findByRoomIdOrderBySentAtDesc(String roomId, Pageable pageable);
}
