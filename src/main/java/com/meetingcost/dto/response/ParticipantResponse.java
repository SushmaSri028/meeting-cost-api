package com.meetingcost.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ParticipantResponse {
    private String email;
    private String displayName;
    private String estimatedTitle;
    private BigDecimal estimatedAnnualSalary;
    private BigDecimal estimatedHourlyRate;
    private BigDecimal costContributionUsd;
}