package com.meetingcost.controller;

import com.meetingcost.entity.User;
import com.meetingcost.repository.UserRepository;
import com.meetingcost.service.CalendarSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarSyncService calendarSyncService;
    private final UserRepository userRepository;

    // Trigger a manual sync for the current user (for testing)
    @PostMapping("/sync")
    public ResponseEntity<Map<String, String>> manualSync() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getGoogleRefreshToken() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Google Calendar not connected. Visit /oauth2/authorization/google"));
        }

        try {
            calendarSyncService.syncCalendarForUser(user);
            return ResponseEntity.ok(Map.of("message", "Sync complete for " + email));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Sync failed: " + e.getMessage()));
        }
    }

    // Check connection status
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> connectionStatus() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(Map.of(
                "connected", user.getGoogleRefreshToken() != null,
                "email",     email,
                "connectUrl", "/oauth2/authorization/google"
        ));
    }
}