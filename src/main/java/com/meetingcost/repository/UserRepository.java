package com.meetingcost.repository;

import com.meetingcost.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // Spring Data JPA reads the method name and generates the SQL automatically:
    // SELECT * FROM users WHERE email = :email LIMIT 1
    Optional<User> findByEmail(String email);

    // Generates: SELECT COUNT(*) > 0 FROM users WHERE email = :email
    boolean existsByEmail(String email);
}