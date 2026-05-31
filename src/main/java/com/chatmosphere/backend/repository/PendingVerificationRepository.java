package com.chatmosphere.backend.repository;

import com.chatmosphere.backend.entity.PendingVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PendingVerificationRepository extends JpaRepository<PendingVerification, Long> {
    Optional<PendingVerification> findByEmail(String email);
    Optional<PendingVerification> findByToken(String token);
}
