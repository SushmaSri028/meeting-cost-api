package com.meetingcost.controller;

import com.meetingcost.service.LiveCostTickerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final LiveCostTickerService liveCostTickerService;

    @MessageMapping("/meeting/start")
    public void startMeeting(@Payload String meetingId) {
        log.info("WebSocket: start tracking request for meeting {}", meetingId);
        liveCostTickerService.startTracking(meetingId.trim().replace("\"", ""));
    }

    @MessageMapping("/meeting/stop")
    public void stopMeeting(@Payload String meetingId) {
        log.info("WebSocket: stop tracking request for meeting {}", meetingId);
        liveCostTickerService.stopTracking(meetingId.trim().replace("\"", ""));
    }
}