package com.meetingcost.controller;

import com.meetingcost.dto.request.LoginRequest;
import com.meetingcost.dto.request.RegisterRequest;
import com.meetingcost.dto.response.AuthResponse;
import com.meetingcost.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // POST /api/auth/register
    // @Valid triggers the validation annotations on RegisterRequest
    // If validation fails, GlobalExceptionHandler.handleValidationErrors() is called
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);  // 201 Created
    }

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);  // 200 OK
    }
}