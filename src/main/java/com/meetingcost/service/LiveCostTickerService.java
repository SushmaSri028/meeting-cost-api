package com.meetingcost.service;

import com.meetingcost.dto.LiveCostUpdate;
import com.meetingcost.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveCostTickerService {

    private final SimpMessagingTemplate messagingTemplate;
    private final MeetingRepository meetingRepository;

    // meetingId -> when tracking started
    private final ConcurrentHashMap<String, Instant> activeMeetings = new ConcurrentHashMap<>();

    public void startTracking(String meetingId) {
        activeMeetings.put(meetingId, Instant.now());
        log.info("Started live cost tracking for meeting: {}", meetingId);
    }

    public void stopTracking(String meetingId) {
        activeMeetings.remove(meetingId);
        log.info("Stopped live cost tracking for meeting: {}", meetingId);
    }

    @Scheduled(fixedDelay = 1000)   // broadcast every second
    public void broadcastCosts() {
        activeMeetings.forEach((meetingId, startTime) -> {
            long elapsedSeconds = Duration.between(startTime, Instant.now()).toSeconds();

            meetingRepository.findById(UUID.fromString(meetingId)).ifPresent(meeting -> {
                BigDecimal perSecondRate = calculatePerSecondRate(meeting);
                BigDecimal currentCost = perSecondRate
                        .multiply(BigDecimal.valueOf(elapsedSeconds))
                        .setScale(2, RoundingMode.HALF_UP);

                LiveCostUpdate update = LiveCostUpdate.builder()
                        .meetingId(meetingId)
                        .meetingTitle(meeting.getTitle())
                        .elapsedSeconds(elapsedSeconds)
                        .currentCostUsd(currentCost)
                        .participantCount(meeting.getParticipantCount())
                        .formattedCost(String.format("$%.2f", currentCost))
                        .formattedElapsed(formatElapsed(elapsedSeconds))
                        .build();

                messagingTemplate.convertAndSend("/topic/cost/" + meetingId, update);
            });
        });
    }

    private BigDecimal calculatePerSecondRate(com.meetingcost.entity.Meeting meeting) {
        if (meeting.getEstimatedCostUsd() == null || meeting.getDurationMinutes() == 0) {
            return BigDecimal.valueOf(0.05); // default $0.05/sec for demo
        }
        long totalSeconds = (long) meeting.getDurationMinutes() * 60;
        return meeting.getEstimatedCostUsd()
                .divide(BigDecimal.valueOf(totalSeconds), 6, RoundingMode.HALF_UP);
    }

    private String formatElapsed(long totalSeconds) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}