package com.meetingcost.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "participants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // MANY participants belong to ONE meeting
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "estimated_title", length = 255)
    private String estimatedTitle;

    @Column(name = "estimated_annual_salary", precision = 10, scale = 2)
    private BigDecimal estimatedAnnualSalary;

    @Column(name = "estimated_hourly_rate", precision = 10, scale = 4)
    private BigDecimal estimatedHourlyRate;    // annualSalary / 52 / 40

    @Column(name = "cost_contribution_usd", precision = 10, scale = 2)
    private BigDecimal costContributionUsd;   // hourlyRate * durationHours

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}