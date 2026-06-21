package com.meetingcost.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.Events;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.UserCredentials;
import com.meetingcost.entity.Meeting;
import com.meetingcost.entity.Participant;
import com.meetingcost.entity.User;
import com.meetingcost.repository.MeetingRepository;
import com.meetingcost.repository.ParticipantRepository;
import com.meetingcost.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j   // Lombok: generates 'log' field — use log.info(), log.error() etc.
public class CalendarSyncService {

    private final UserRepository userRepository;
    private final MeetingRepository meetingRepository;
    private final ParticipantRepository participantRepository;
    private final CostCalculationService costCalculationService;

    @Value("${app.google.client-id}")
    private String googleClientId;

    @Value("${app.google.client-secret}")
    private String googleClientSecret;

    // ── SCHEDULED JOB ─────────────────────────────────────────
    // fixedDelay = 900000ms = 15 minutes
    // Runs 15 minutes AFTER the previous run completes (not a fixed clock time)
    // initialDelay = 30000ms = waits 30 seconds after app startup before first run
    @Scheduled(fixedDelay = 900000, initialDelay = 30000)
    public void syncAllUsers() {
        log.info("Starting scheduled calendar sync for all users...");

        // Find all users who have connected Google Calendar
        List<User> connectedUsers = userRepository.findAll().stream()
                .filter(u -> u.getGoogleRefreshToken() != null
                        && !u.getGoogleRefreshToken().isBlank())
                .toList();

        log.info("Found {} users with connected Google Calendar", connectedUsers.size());

        for (User user : connectedUsers) {
            try {
                syncCalendarForUser(user);
            } catch (Exception e) {
                // Log error but continue with other users
                // One user's failure should not stop the sync for everyone
                log.error("Failed to sync calendar for user {}: {}", user.getEmail(), e.getMessage());
            }
        }

        log.info("Calendar sync complete.");
    }

    // ── SYNC ONE USER ─────────────────────────────────────────
    @Transactional
    public void syncCalendarForUser(User user) throws IOException, GeneralSecurityException {
        log.info("Syncing calendar for user: {}", user.getEmail());

        // 1. Build Google Calendar client using stored OAuth2 tokens
        Calendar calendarService = buildCalendarClient(user);

        // 2. Fetch events from the last 30 days to next 30 days
        OffsetDateTime now      = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime timeMin  = now.minusDays(30);
        OffsetDateTime timeMax  = now.plusDays(30);

        com.google.api.client.util.DateTime googleTimeMin =
                new com.google.api.client.util.DateTime(
                        Date.from(timeMin.toInstant()));
        com.google.api.client.util.DateTime googleTimeMax =
                new com.google.api.client.util.DateTime(
                        Date.from(timeMax.toInstant()));

        // 3. Call Google Calendar API
        Events events = calendarService.events().list("primary")
                .setTimeMin(googleTimeMin)
                .setTimeMax(googleTimeMax)
                .setSingleEvents(true)       // expand recurring events into individual instances
                .setOrderBy("startTime")
                .setMaxResults(250)          // max per page
                .execute();

        List<Event> googleEvents = events.getItems();
        log.info("Fetched {} events from Google Calendar for {}", googleEvents.size(), user.getEmail());

        // 4. Process each event
        int synced = 0;
        for (Event event : googleEvents) {
            if (isValidMeeting(event)) {
                processEvent(user, event);
                synced++;
            }
        }

        // 5. Update last sync time on user
        user.setGoogleTokenExpiry(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);

        log.info("Synced {} meetings for user {}", synced, user.getEmail());
    }

    // ── BUILD GOOGLE CALENDAR CLIENT ──────────────────────────
    private Calendar buildCalendarClient(User user) throws IOException, GeneralSecurityException {

        // Create credentials from stored tokens
        // UserCredentials handles token refresh automatically using the refresh token
        UserCredentials credentials = UserCredentials.newBuilder()
                .setClientId(googleClientId)
                .setClientSecret(googleClientSecret)
                .setRefreshToken(user.getGoogleRefreshToken())
                .setAccessToken(new AccessToken(
                        user.getGoogleAccessToken() != null ? user.getGoogleAccessToken() : "",
                        user.getGoogleTokenExpiry() != null
                                ? Date.from(user.getGoogleTokenExpiry().toInstant())
                                : new Date()
                ))
                .build();

        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),   // HTTPS transport
                GsonFactory.getDefaultInstance(),                // JSON parser
                new HttpCredentialsAdapter(credentials)          // attaches token to each request
        )
                .setApplicationName("MeetingCost.io")
                .build();
    }

    // ── PROCESS ONE EVENT ─────────────────────────────────────
    @Transactional
    public void processEvent(User user, Event event) {

        // Check if this event already exists in our DB
        Meeting meeting = meetingRepository
                .findByUserAndCalendarEventId(user, event.getId())
                .orElse(null);

        // Parse start and end times from Google's event format
        OffsetDateTime startTime = parseEventDateTime(event.getStart());
        OffsetDateTime endTime   = parseEventDateTime(event.getEnd());

        if (startTime == null || endTime == null) {
            return;   // all-day events have no time — skip them
        }

        // Calculate duration
        long durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes();

        if (meeting == null) {
            // New event — create meeting record
            meeting = Meeting.builder()
                    .user(user)
                    .calendarEventId(event.getId())
                    .title(event.getSummary() != null ? event.getSummary() : "Untitled Meeting")
                    .description(event.getDescription())
                    .startTime(startTime)
                    .endTime(endTime)
                    .durationMinutes((int) durationMinutes)
                    .lastSyncedAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();
        } else {
            // Existing event — update in case title/time changed
            meeting.setTitle(event.getSummary() != null ? event.getSummary() : "Untitled Meeting");
            meeting.setStartTime(startTime);
            meeting.setEndTime(endTime);
            meeting.setDurationMinutes((int) durationMinutes);
            meeting.setLastSyncedAt(OffsetDateTime.now(ZoneOffset.UTC));

            // Clear old participants — we'll rebuild them
            participantRepository.deleteByMeeting(meeting);
            meeting.getParticipants().clear();
        }

        // Build participant list
        List<Participant> participants = buildParticipants(meeting, event, user);
        meeting.setParticipantCount(participants.size());

        // Compute total meeting cost using the cost engine
        BigDecimal totalCost = costCalculationService.computeMeetingCost(
                participants, durationMinutes);
        meeting.setEstimatedCostUsd(totalCost);

        // Save meeting (cascade saves participants)
        meetingRepository.save(meeting);
    }

    // ── BUILD PARTICIPANT LIST FROM GOOGLE EVENT ──────────────
    private List<Participant> buildParticipants(Meeting meeting, Event event, User organizer) {
        List<Participant> participants = new ArrayList<>();

        if (event.getAttendees() == null || event.getAttendees().isEmpty()) {
            // Solo event — just the organizer
            Participant solo = costCalculationService.buildParticipant(
                    meeting, organizer.getEmail(), organizer.getDisplayName());
            participants.add(solo);
        } else {
            for (EventAttendee attendee : event.getAttendees()) {
                if (Boolean.TRUE.equals(attendee.getResource())) {
                    continue;   // skip room/resource attendees (e.g., "Conference Room B")
                }

                Participant p = costCalculationService.buildParticipant(
                        meeting,
                        attendee.getEmail(),
                        attendee.getDisplayName());
                participants.add(p);
            }
        }

        meeting.getParticipants().addAll(participants);
        return participants;
    }

    // ── UTILITY: Check if event is a real meeting ─────────────
    private boolean isValidMeeting(Event event) {
        // Skip cancelled events
        if ("cancelled".equals(event.getStatus())) return false;
        // Skip all-day events (they have date only, not dateTime)
        if (event.getStart() == null || event.getStart().getDateTime() == null) return false;
        // Skip events with no title
        if (event.getSummary() == null || event.getSummary().isBlank()) return false;
        return true;
    }

    // ── UTILITY: Parse Google DateTime to OffsetDateTime ──────
    private OffsetDateTime parseEventDateTime(
            com.google.api.services.calendar.model.EventDateTime eventDateTime) {
        if (eventDateTime == null || eventDateTime.getDateTime() == null) {
            return null;
        }
        long millis = eventDateTime.getDateTime().getValue();
        return OffsetDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(millis), ZoneOffset.UTC);
    }
}