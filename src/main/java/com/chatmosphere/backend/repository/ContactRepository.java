package com.chatmosphere.backend.repository;

import com.chatmosphere.backend.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByUserUsername(String username);
    Optional<Contact> findByUserUsernameAndContactUserUsername(String ownerUsername, String contactUsername);
    boolean existsByUserUsernameAndContactUserMobileNumber(String ownerUsername, String mobileNumber);
}
