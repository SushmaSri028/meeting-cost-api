package com.meetingcost.service;

import com.meetingcost.entity.Meeting;
import com.meetingcost.entity.Participant;
import com.meetingcost.entity.SalaryLookup;
import com.meetingcost.repository.SalaryLookupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.OptionalDouble;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CostCalculationService {

    private final SalaryLookupRepository salaryLookupRepository;

    // Work hours constants
    private static final BigDecimal WEEKS_PER_YEAR  = new BigDecimal("52");
    private static final BigDecimal HOURS_PER_WEEK  = new BigDecimal("40");
    private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");

    // ── COMPUTE TOTAL MEETING COST ────────────────────────────
    // This is the core algorithm — Java Streams aggregating participant costs
    public BigDecimal computeMeetingCost(List<Participant> participants, long durationMinutes) {

        // Convert duration to hours as BigDecimal for precision
        BigDecimal durationHours = BigDecimal.valueOf(durationMinutes)
                .divide(MINUTES_PER_HOUR, 4, RoundingMode.HALF_UP);

        // Java Stream pipeline:
        // 1. For each participant, estimate their annual salary
        // 2. Compute hourly rate = salary / 52 / 40
        // 3. Compute cost contribution = hourly rate * duration hours
        // 4. Set cost on the participant object
        // 5. Sum all contributions
        return participants.stream()
                .map(participant -> {
                    BigDecimal annualSalary = estimateSalary(participant.getEmail(),
                            participant.getEstimatedTitle());
                    BigDecimal hourlyRate = annualSalary
                            .divide(WEEKS_PER_YEAR, 4, RoundingMode.HALF_UP)
                            .divide(HOURS_PER_WEEK, 4, RoundingMode.HALF_UP);

                    BigDecimal contribution = hourlyRate
                            .multiply(durationHours)
                            .setScale(2, RoundingMode.HALF_UP);

                    // Set values back on the participant entity
                    participant.setEstimatedAnnualSalary(annualSalary);
                    participant.setEstimatedHourlyRate(hourlyRate);
                    participant.setCostContributionUsd(contribution);

                    return contribution;
                })
                // Collectors.reducing sums all BigDecimal values safely
                .collect(Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ── ESTIMATE SALARY BY EMAIL / TITLE ─────────────────────
    // Tries to match participant title to salary lookup table
    private BigDecimal estimateSalary(String email, String estimatedTitle) {
        // Load all global defaults from DB
        List<SalaryLookup> defaults = salaryLookupRepository.findByUserIsNull();

        if (estimatedTitle != null && !estimatedTitle.isBlank()) {
            // Exact match first
            Optional<SalaryLookup> exact = defaults.stream()
                    .filter(s -> s.getTitleKeyword()
                            .equalsIgnoreCase(estimatedTitle))
                    .findFirst();
            if (exact.isPresent()) return exact.get().getAnnualSalary();

            // Partial match — check if the estimated title CONTAINS a keyword
            Optional<SalaryLookup> partial = defaults.stream()
                    .filter(s -> estimatedTitle.toLowerCase()
                            .contains(s.getTitleKeyword().toLowerCase()))
                    .findFirst();
            if (partial.isPresent()) return partial.get().getAnnualSalary();
        }

        // Try to infer title from email domain (e.g., @eng.company.com = engineer)
        if (email != null && email.contains("eng")) {
            Optional<SalaryLookup> engMatch = defaults.stream()
                    .filter(s -> s.getTitleKeyword().equalsIgnoreCase("Software Engineer"))
                    .findFirst();
            if (engMatch.isPresent()) return engMatch.get().getAnnualSalary();
        }

        // Fall back to global default salary
        return defaults.stream()
                .filter(s -> s.getTitleKeyword().equalsIgnoreCase("Default"))
                .findFirst()
                .map(SalaryLookup::getAnnualSalary)
                .orElse(new BigDecimal("100000"));
    }

    // ── BUILD A PARTICIPANT ENTITY ────────────────────────────
    public Participant buildParticipant(Meeting meeting, String email, String displayName) {
        // Try to guess title from display name or email
        String guessedTitle = guessTitle(email, displayName);

        return Participant.builder()
                .meeting(meeting)
                .email(email)
                .displayName(displayName)
                .estimatedTitle(guessedTitle)
                .build();
    }

    // ── GUESS JOB TITLE FROM NAME/EMAIL ──────────────────────
    private String guessTitle(String email, String displayName) {
        if (email == null) return null;
        String lower = email.toLowerCase();

        // Simple heuristics based on email patterns
        if (lower.contains("ceo") || lower.contains("chief.executive")) return "CEO";
        if (lower.contains("cto") || lower.contains("chief.tech"))      return "CTO";
        if (lower.contains("vp") || lower.contains("vice"))              return "VP Engineering";
        if (lower.contains("manager") || lower.contains("mgr"))          return "Engineering Manager";
        if (lower.contains("senior") || lower.contains("sr."))           return "Senior Engineer";
        if (lower.contains("product") || lower.contains("pm."))          return "Product Manager";
        if (lower.contains("design"))                                     return "Designer";
        if (lower.contains("data"))                                       return "Data Scientist";
        if (lower.contains("recruit") || lower.contains("hr"))           return "Recruiter";

        return null;   // will fall back to Default salary in estimateSalary()
    }

    // ── COMPUTE STATS USING STREAMS ───────────────────────────
    // Called by MeetingService for the /api/meetings/stats endpoint
    public Map<String, Object> computeStats(List<Meeting> meetings) {

        // Total cost of all meetings — BigDecimal stream reduction
        BigDecimal totalCost = meetings.stream()
                .filter(m -> m.getEstimatedCostUsd() != null)
                .map(Meeting::getEstimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Average cost per meeting
        OptionalDouble avgCostOpt = meetings.stream()
                .filter(m -> m.getEstimatedCostUsd() != null)
                .mapToDouble(m -> m.getEstimatedCostUsd().doubleValue())
                .average();

        BigDecimal avgCost = avgCostOpt.isPresent()
                ? BigDecimal.valueOf(avgCostOpt.getAsDouble()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Most expensive single meeting
        Optional<Meeting> mostExpensive = meetings.stream()
                .filter(m -> m.getEstimatedCostUsd() != null)
                .max(java.util.Comparator.comparing(Meeting::getEstimatedCostUsd));

        // Count by day of week using Collectors.groupingBy
        Map<String, Long> byDayOfWeek = meetings.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getStartTime().getDayOfWeek().getDisplayName(
                                java.time.format.TextStyle.FULL,
                                java.util.Locale.ENGLISH),
                        Collectors.counting()
                ));

        // Total cost by day of week
        Map<String, BigDecimal> costByDay = meetings.stream()
                .filter(m -> m.getEstimatedCostUsd() != null)
                .collect(Collectors.groupingBy(
                        m -> m.getStartTime().getDayOfWeek().getDisplayName(
                                java.time.format.TextStyle.FULL,
                                java.util.Locale.ENGLISH),
                        Collectors.mapping(
                                Meeting::getEstimatedCostUsd,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
                        )
                ));

        return Map.of(
                "totalMeetings",    meetings.size(),
                "totalCostUsd",     totalCost,
                "avgCostUsd",       avgCost,
                "mostExpensive",    mostExpensive.map(Meeting::getTitle).orElse("N/A"),
                "mostExpensiveCost",mostExpensive.map(Meeting::getEstimatedCostUsd)
                        .orElse(BigDecimal.ZERO),
                "meetingsByDay",    byDayOfWeek,
                "costByDay",        costByDay
        );
    }
}