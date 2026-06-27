package com.meetingcost.service;

import com.meetingcost.entity.Meeting;
import com.meetingcost.entity.User;
import com.meetingcost.repository.MeetingRepository;
import com.meetingcost.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MicrosoftCalendarService {

    private final MeetingRepository meetingRepository;
    private final UserRepository    userRepository;
    private final RestTemplate      restTemplate;

    @Value("${spring.security.oauth2.client.registration.microsoft.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.microsoft.client-secret}")
    private String clientSecret;

    // Flexible parser: handles "2024-06-01T10:00:00Z", "2024-06-01T10:00:00.0000000",
    // "2024-06-01T10:00:00+05:30", etc.
    private static final DateTimeFormatter MS_DATETIME = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 7, true).optionalEnd()
            .optionalStart().appendOffsetId().optionalEnd()
            .toFormatter();

    // -------------------------------------------------------------------------
    // PUBLIC: called by CalendarController
    // -------------------------------------------------------------------------

    @Transactional
    public int syncCalendar(User user) {
        // 1. Ensure we have a valid (non-expired) access token
        String accessToken = refreshTokenIfNeeded(user);

        // 2. Fetch events from Graph API (last 30 days → next 30 days)
        String now   = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String later = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String url = "https://graph.microsoft.com/v1.0/me/calendarView"
                + "?startDateTime=" + now
                + "&endDateTime=" + later
                + "&$top=100"
                + "&$select=id,subject,bodyPreview,start,end,attendees,organizer";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Graph API returned: " + response.getStatusCode());
        }

        List<Map<String, Object>> events =
                (List<Map<String, Object>>) response.getBody().get("value");

        if (events == null || events.isEmpty()) {
            log.info("No Outlook events found for user {}", user.getEmail());
            return 0;
        }

        // 3. Upsert each event
        int count = 0;
        for (Map<String, Object> event : events) {
            try {
                count += upsertEvent(user, event);
            } catch (Exception e) {
                log.warn("Skipping Outlook event due to error: {}", e.getMessage());
            }
        }

        log.info("Synced {} Outlook meetings for {}", count, user.getEmail());
        return count;
    }

    // -------------------------------------------------------------------------
    // TOKEN REFRESH
    // Checks if the stored token expires within 5 minutes; if so, exchanges
    // the refresh token for a new access token and persists it.
    // Returns the valid access token to use for the API call.
    // -------------------------------------------------------------------------

    private String refreshTokenIfNeeded(User user) {
        boolean expired = user.getMicrosoftTokenExpiry() == null
                || user.getMicrosoftTokenExpiry().isBefore(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));

        if (!expired) {
            return user.getMicrosoftAccessToken();
        }

        log.info("Microsoft token expired for {} — refreshing...", user.getEmail());

        if (user.getMicrosoftRefreshToken() == null) {
            throw new RuntimeException(
                    "Microsoft session expired and no refresh token available. Please sign in with Microsoft again.");
        }

        // POST to Microsoft token endpoint
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type",    "refresh_token");
        body.add("client_id",     clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", user.getMicrosoftRefreshToken());
        body.add("scope",
                "openid profile email " +
                        "https://graph.microsoft.com/Calendars.Read " +
                        "https://graph.microsoft.com/User.Read " +
                        "offline_access");

        ResponseEntity<Map> tokenResponse = restTemplate.exchange(
                "https://login.microsoftonline.com/common/oauth2/v2.0/token",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);

        if (!tokenResponse.getStatusCode().is2xxSuccessful() || tokenResponse.getBody() == null) {
            throw new RuntimeException(
                    "Token refresh failed: " + tokenResponse.getStatusCode() +
                            ". Please sign in with Microsoft again.");
        }

        Map<String, Object> tokens = tokenResponse.getBody();

        String newAccessToken  = (String) tokens.get("access_token");
        String newRefreshToken = tokens.containsKey("refresh_token")
                ? (String) tokens.get("refresh_token")
                : user.getMicrosoftRefreshToken();   // keep old one if not rotated
        int    expiresIn       = tokens.containsKey("expires_in")
                ? ((Number) tokens.get("expires_in")).intValue()
                : 3600;

        // Persist new tokens
        user.setMicrosoftAccessToken(newAccessToken);
        user.setMicrosoftRefreshToken(newRefreshToken);
        user.setMicrosoftTokenExpiry(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(expiresIn));
        userRepository.save(user);

        log.info("Microsoft token refreshed successfully for {}", user.getEmail());
        return newAccessToken;
    }

    // -------------------------------------------------------------------------
    // UPSERT a single calendar event into the meetings table
    // -------------------------------------------------------------------------

    private int upsertEvent(User user, Map<String, Object> event) {
        String msId = (String) event.get("id");
        if (msId == null) return 0;

        String calendarEventId = "MS_" + msId;

        // Parse start/end
        Map<String, String> startMap = (Map<String, String>) event.get("start");
        Map<String, String> endMap   = (Map<String, String>) event.get("end");
        if (startMap == null || endMap == null) return 0;

        OffsetDateTime startTime = parseDateTime(startMap.get("dateTime"), startMap.get("timeZone"));
        OffsetDateTime endTime   = parseDateTime(endMap.get("dateTime"),   endMap.get("timeZone"));
        if (startTime == null || endTime == null) return 0;

        int durationMinutes = (int) java.time.Duration.between(startTime, endTime).toMinutes();
        if (durationMinutes <= 0) return 0;

        String title = (String) event.getOrDefault("subject", "Untitled Meeting");

        // Count attendees
        List<Map<String, Object>> attendees =
                (List<Map<String, Object>>) event.getOrDefault("attendees", List.of());
        int participantCount = Math.max(1, attendees.size());

        // Estimate cost: assume avg $75/hr blended rate
        BigDecimal costPerPersonPerHour = new BigDecimal("75.00");
        BigDecimal hours = BigDecimal.valueOf(durationMinutes).divide(BigDecimal.valueOf(60), 4,
                java.math.RoundingMode.HALF_UP);
        BigDecimal estimatedCost = costPerPersonPerHour
                .multiply(hours)
                .multiply(BigDecimal.valueOf(participantCount))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        // Upsert
        Optional<Meeting> existing = meetingRepository.findByUserAndCalendarEventId(user, calendarEventId);
        Meeting meeting = existing.orElse(Meeting.builder()
                .user(user)
                .calendarEventId(calendarEventId)
                .build());

        meeting.setTitle(title);
        meeting.setStartTime(startTime);
        meeting.setEndTime(endTime);
        meeting.setDurationMinutes(durationMinutes);
        meeting.setParticipantCount(participantCount);
        meeting.setEstimatedCostUsd(estimatedCost);
        meeting.setLastSyncedAt(OffsetDateTime.now(ZoneOffset.UTC));

        meetingRepository.save(meeting);
        return existing.isEmpty() ? 1 : 0;
    }

    // -------------------------------------------------------------------------
    // Parse Microsoft's datetime strings (UTC or floating) → OffsetDateTime
    // -------------------------------------------------------------------------

    private OffsetDateTime parseDateTime(String dateTime, String timeZone) {
        if (dateTime == null) return null;
        try {
            // Microsoft sends floating datetimes (no offset) when timeZone field is present
            if (!dateTime.contains("Z") && !dateTime.contains("+") && !dateTime.contains("-", 10)) {
                // Treat as UTC (Graph calendarView always returns UTC for calendarView endpoint)
                dateTime = dateTime + "Z";
            }
            return OffsetDateTime.parse(dateTime, MS_DATETIME).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            log.warn("Could not parse Microsoft datetime '{}': {}", dateTime, e.getMessage());
            return null;
        }
    }
}