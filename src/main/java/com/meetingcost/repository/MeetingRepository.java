package com.meetingcost.repository;

import com.meetingcost.entity.Meeting;
import com.meetingcost.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    // Find a specific meeting by Google's event ID for a user (for upsert logic)
    Optional<Meeting> findByUserAndCalendarEventId(User user, String calendarEventId);

    // Get all meetings for a user in a date range, sorted by start time
    List<Meeting> findByUserAndStartTimeBetweenOrderByStartTimeDesc(
            User user, OffsetDateTime start, OffsetDateTime end);

    // Custom JPQL query — JOIN FETCH loads participants in the same query
    @Query("SELECT m FROM Meeting m " +
            "LEFT JOIN FETCH m.participants " +
            "WHERE m.user = :user " +
            "AND m.startTime BETWEEN :start AND :end " +
            "ORDER BY m.startTime DESC")
    List<Meeting> findMeetingsWithParticipants(
            @Param("user") User user,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    // Used for stats: all meetings for user with cost data
    @Query("SELECT m FROM Meeting m WHERE m.user = :user " +
            "AND m.estimatedCostUsd IS NOT NULL " +
            "ORDER BY m.estimatedCostUsd DESC")
    List<Meeting> findAllWithCostByUser(@Param("user") User user);
}