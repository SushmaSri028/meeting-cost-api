package com.meetingcost.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class LiveCostUpdate {
    private String meetingId;
    private String meetingTitle;
    private long elapsedSeconds;
    private BigDecimal currentCostUsd;
    private int participantCount;
    private String formattedCost;      // "$87.50"
    private String formattedElapsed;   // "00:02:35"
}