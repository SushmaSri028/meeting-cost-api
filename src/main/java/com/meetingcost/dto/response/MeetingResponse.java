package com.meetingcost.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class MeetingResponse {
    private UUID id;
    private String title;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Integer durationMinutes;
    private BigDecimal estimatedCostUsd;
    private Integer participantCount;
    private List<ParticipantResponse> participants;
}