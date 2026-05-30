package com.chatmosphere.backend.repository;

import com.chatmosphere.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByMobileNumber(String mobileNumber);
    Optional<User> findByEmail(String email);
    List<User> findByUsernameNot(String username);
}
