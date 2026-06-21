package com.meetingcost.service;

import com.meetingcost.dto.request.LoginRequest;
import com.meetingcost.dto.request.RegisterRequest;
import com.meetingcost.dto.response.AuthResponse;
import com.meetingcost.entity.User;
import com.meetingcost.repository.UserRepository;
import com.meetingcost.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;   // BCrypt (configured in SecurityConfig)
    private final JwtTokenProvider jwtTokenProvider;

    // ── REGISTER ──────────────────────────────────────────────
    @Transactional    // wraps the entire method in a DB transaction — rolls back on exception
    public AuthResponse register(RegisterRequest request) {

        // 1. Check if email is already taken
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "Email already registered: " + request.getEmail()
            );
        }

        // 2. Hash the password — NEVER store plain text passwords
        // BCrypt generates a random salt and hashes: "$2a$10$..."
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        // 3. Build and save the User entity using Lombok @Builder
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(hashedPassword)
                .displayName(request.getDisplayName())
                .build();

        userRepository.save(user);   // INSERT INTO users ...

        // 4. Generate JWT for the new user
        String token = jwtTokenProvider.generateToken(user.getEmail());

        // 5. Return AuthResponse DTO (never return the User entity directly)
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .expiresIn(jwtTokenProvider.getExpirationMs())
                .build();
    }

    // ── LOGIN ─────────────────────────────────────────────────
    public AuthResponse login(LoginRequest request) {

        // 1. Find user by email — throw generic error (don't reveal if email exists)
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        // 2. Verify password against the stored BCrypt hash
        // passwordEncoder.matches() handles the salt comparison automatically
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        // 3. Generate and return fresh token
        String token = jwtTokenProvider.generateToken(user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .expiresIn(jwtTokenProvider.getExpirationMs())
                .build();
    }
}