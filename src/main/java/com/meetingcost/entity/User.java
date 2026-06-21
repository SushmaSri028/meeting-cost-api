package com.meetingcost.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")                    // maps to the "users" table in PostgreSQL
@Getter                                   // Lombok: generates getUser(), getEmail(), etc.
@Setter                                   // Lombok: generates setUser(), setEmail(), etc.
@NoArgsConstructor                        // Lombok: generates User() — required by JPA
@AllArgsConstructor                       // Lombok: generates User(id, email, ...) constructor
@Builder                                  // Lombok: enables User.builder().email("...").build()
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "google_access_token", columnDefinition = "TEXT")
    private String googleAccessToken;

    @Column(name = "google_refresh_token", columnDefinition = "TEXT")
    private String googleRefreshToken;

    @Column(name = "google_token_expiry")
    private OffsetDateTime googleTokenExpiry;

    @CreationTimestamp                    // Hibernate sets this automatically on INSERT
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp                      // Hibernate updates this automatically on UPDATE
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}