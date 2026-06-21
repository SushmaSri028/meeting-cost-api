package com.meetingcost.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "meetings",
        uniqueConstraints = {
                // Matches the UNIQUE constraint we defined in V1 SQL migration
                @UniqueConstraint(columnNames = {"user_id", "calendar_event_id"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Many meetings belong to ONE user
    @ManyToOne(fetch = FetchType.LAZY)  // LAZY = don't load User unless explicitly accessed
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "calendar_event_id", nullable = false, length = 500)
    private String calendarEventId;   // Google's event ID — used to prevent duplicates

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "estimated_cost_usd", precision = 10, scale = 2)
    private BigDecimal estimatedCostUsd;

    @Column(name = "participant_count")
    private Integer participantCount;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // ONE meeting has MANY participants
    // cascade = ALL means saving/deleting Meeting also saves/deletes its Participants
    // orphanRemoval = true means removing a Participant from this list deletes it from DB
    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Participant> participants = new ArrayList<>();
}