package com.meetingcost.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class MeetingStatsResponse {
    private int totalMeetings;
    private BigDecimal totalCostUsd;
    private BigDecimal avgCostUsd;
    private String mostExpensiveMeeting;
    private BigDecimal mostExpensiveCostUsd;
    private Map<String, Long> meetingsByDay;
    private Map<String, BigDecimal> costByDay;
}