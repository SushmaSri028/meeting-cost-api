package com.meetingcost.repository;

import com.meetingcost.entity.Meeting;
import com.meetingcost.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    List<Participant> findByMeeting(Meeting meeting);

    void deleteByMeeting(Meeting meeting);
}