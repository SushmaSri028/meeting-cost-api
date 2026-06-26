package com.meetingcost.service;

import com.meetingcost.entity.Meeting;
import com.meetingcost.entity.User;
import com.meetingcost.repository.MeetingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;

@Service
public class MicrosoftCalendarService {

    private static final Logger log = LoggerFactory.getLogger(MicrosoftCalendarService.class);
    private static final String GRAPH_BASE    = "https://graph.microsoft.com/v1.0";
    private static final int SYNC_DAYS_BACK   = 7;
    private static final int SYNC_DAYS_FORWARD = 30;
    private static final double DEFAULT_SALARY = 100_000.0;

    private final MeetingRepository meetingRepository;
    private final RestTemplate      restTemplate;

    public MicrosoftCalendarService(MeetingRepository meetingRepository) {
        this.meetingRepository = meetingRepository;
        this.restTemplate      = new RestTemplate();
    }

    // ─── Public entry point ───────────────────────────────────────────────────

    public int syncCalendar(User user) {
        String accessToken = user.getMicrosoftAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("No Microsoft access token. Sign in with Microsoft first.");
        }

        List<Map<String, Object>> events = fetchOutlookEvents(accessToken);
        int count = 0;

        for (Map<String, Object> event : events) {
            try {
                Meeting meeting = convertToMeeting(event, user);
                if (meeting != null) {
                    meetingRepository.save(meeting);
                    count++;
                }
            } catch (Exception e) {
                log.warn("Skipping event due to error: {}", e.getMessage());
            }
        }

        log.info("Synced {} Outlook events for {}", count, user.getEmail());
        return count;
    }

    // ─── Fetch events from Microsoft Graph API ────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchOutlookEvents(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/json");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String startDateTime = OffsetDateTime.now(ZoneOffset.UTC)
                .minusDays(SYNC_DAYS_BACK)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String endDateTime = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(SYNC_DAYS_FORWARD)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String url = GRAPH_BASE + "/me/calendarView"
                + "?startDateTime=" + startDateTime
                + "&endDateTime="   + endDateTime
                + "&$select=id,subject,start,end,attendees,organizer"
                + "&$top=50";

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object value = response.getBody().get("value");
                if (value instanceof List) {
                    return (List<Map<String, Object>>) value;
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch Outlook events: {}", e.getMessage());
            throw new RuntimeException(
                    "Could not fetch Outlook calendar. Your Microsoft session may have expired.");
        }

        return Collections.emptyList();
    }

    // ─── Convert Graph API event to Meeting entity ────────────────────────────

    @SuppressWarnings("unchecked")
    private Meeting convertToMeeting(Map<String, Object> event, User user) {
        String microsoftEventId = (String) event.get("id");
        String title = (String) event.getOrDefault("subject", "Untitled Meeting");

        // Prefix MS_ to avoid collision with Google Calendar IDs
        String calendarEventId = "MS_" + microsoftEventId;

        // Dedup: skip if already synced (reuses existing findByUserAndCalendarEventId)
        if (meetingRepository.findByUserAndCalendarEventId(user, calendarEventId).isPresent()) {
            return null;
        }

        Map<String, String> startMap = (Map<String, String>) event.get("start");
        Map<String, String> endMap   = (Map<String, String>) event.get("end");
        if (startMap == null || endMap == null) return null;

        OffsetDateTime startTime = parseMicrosoftDateTime(startMap);
        OffsetDateTime endTime   = parseMicrosoftDateTime(endMap);
        if (startTime == null || endTime == null) return null;

        long durationMinutes = Duration.between(startTime, endTime).toMinutes();
        if (durationMinutes <= 0) return null;

        // Participant count
        List<Map<String, Object>> attendees =
                (List<Map<String, Object>>) event.getOrDefault("attendees", Collections.emptyList());
        int participantCount = Math.max(1, attendees.size());

        // Cost estimate: (annual salary / 52 weeks / 40 hrs) * participants * hours
        double hourlyRate = DEFAULT_SALARY / 52.0 / 40.0;
        double costValue  = hourlyRate * participantCount * (durationMinutes / 60.0);
        BigDecimal cost   = BigDecimal.valueOf(costValue).setScale(2, RoundingMode.HALF_UP);

        return Meeting.builder()
                .user(user)
                .calendarEventId(calendarEventId)
                .title(title)
                .startTime(startTime)
                .endTime(endTime)
                .durationMinutes((int) durationMinutes)
                .participantCount(participantCount)
                .estimatedCostUsd(cost)
                .lastSyncedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    // ─── Parse Microsoft datetime format ─────────────────────────────────────
    // Microsoft Graph returns: "2024-01-15T10:00:00.0000000" with variable fractional seconds

    private OffsetDateTime parseMicrosoftDateTime(Map<String, String> dateTimeMap) {
        try {
            String dateTimeStr = dateTimeMap.get("dateTime");
            String timeZone    = dateTimeMap.getOrDefault("timeZone", "UTC");

            DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
                    .optionalStart()
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                    .optionalEnd()
                    .toFormatter();

            LocalDateTime ldt  = LocalDateTime.parse(dateTimeStr, formatter);
            ZoneId         zone = ZoneId.of(timeZone);
            return ldt.atZone(zone).toOffsetDateTime();

        } catch (Exception e) {
            log.warn("Could not parse Microsoft datetime: {}", e.getMessage());
            return null;
        }
    }
}