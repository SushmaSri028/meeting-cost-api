package com.meetingcost.service;

import com.meetingcost.dto.response.MeetingResponse;
import com.meetingcost.dto.response.MeetingStatsResponse;
import com.meetingcost.dto.response.ParticipantResponse;
import com.meetingcost.entity.Meeting;
import com.meetingcost.entity.User;
import com.meetingcost.repository.MeetingRepository;
import com.meetingcost.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final CostCalculationService costCalculationService;

    // ── GET CURRENT AUTHENTICATED USER ───────────────────────
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();   // returns email (set as principal in JWT filter)
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // ── GET MEETINGS FOR A DATE RANGE ─────────────────────────
    public List<MeetingResponse> getMeetings(int daysBack, int daysForward) {
        User user = getCurrentUser();
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).minusDays(daysBack);
        OffsetDateTime end   = OffsetDateTime.now(ZoneOffset.UTC).plusDays(daysForward);

        List<Meeting> meetings = meetingRepository
                .findMeetingsWithParticipants(user, start, end);

        // Map entities to DTOs using Java Stream
        return meetings.stream()
                .map(this::toMeetingResponse)
                .collect(Collectors.toList());
    }

    // ── GET STATS ─────────────────────────────────────────────
    public MeetingStatsResponse getStats() {
        User user = getCurrentUser();
        List<Meeting> meetings = meetingRepository.findAllWithCostByUser(user);

        @SuppressWarnings("unchecked")
        Map<String, Object> raw = costCalculationService.computeStats(meetings);

        return MeetingStatsResponse.builder()
                .totalMeetings((int) raw.get("totalMeetings"))
                .totalCostUsd((BigDecimal) raw.get("totalCostUsd"))
                .avgCostUsd((BigDecimal) raw.get("avgCostUsd"))
                .mostExpensiveMeeting((String) raw.get("mostExpensive"))
                .mostExpensiveCostUsd((BigDecimal) raw.get("mostExpensiveCost"))
                .meetingsByDay((Map<String, Long>) raw.get("meetingsByDay"))
                .costByDay((Map<String, BigDecimal>) raw.get("costByDay"))
                .build();
    }

    // ── MAP ENTITY TO DTO ─────────────────────────────────────
    private MeetingResponse toMeetingResponse(Meeting meeting) {
        List<ParticipantResponse> participantDtos = meeting.getParticipants().stream()
                .map(p -> ParticipantResponse.builder()
                        .email(p.getEmail())
                        .displayName(p.getDisplayName())
                        .estimatedTitle(p.getEstimatedTitle())
                        .estimatedAnnualSalary(p.getEstimatedAnnualSalary())
                        .estimatedHourlyRate(p.getEstimatedHourlyRate())
                        .costContributionUsd(p.getCostContributionUsd())
                        .build())
                .collect(Collectors.toList());

        return MeetingResponse.builder()
                .id(meeting.getId())
                .title(meeting.getTitle())
                .startTime(meeting.getStartTime())
                .endTime(meeting.getEndTime())
                .durationMinutes(meeting.getDurationMinutes())
                .estimatedCostUsd(meeting.getEstimatedCostUsd())
                .participantCount(meeting.getParticipantCount())
                .participants(participantDtos)
                .build();
    }
}