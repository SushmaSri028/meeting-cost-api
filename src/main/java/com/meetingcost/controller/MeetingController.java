package com.meetingcost.controller;

import com.meetingcost.dto.response.MeetingResponse;
import com.meetingcost.dto.response.MeetingStatsResponse;
import com.meetingcost.service.MeetingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    // GET /api/meetings?daysBack=30&daysForward=7
    @GetMapping
    public ResponseEntity<List<MeetingResponse>> getMeetings(
            @RequestParam(defaultValue = "30") int daysBack,
            @RequestParam(defaultValue = "7")  int daysForward) {

        return ResponseEntity.ok(meetingService.getMeetings(daysBack, daysForward));
    }

    // GET /api/meetings/stats
    @GetMapping("/stats")
    public ResponseEntity<MeetingStatsResponse> getStats() {
        return ResponseEntity.ok(meetingService.getStats());
    }
}