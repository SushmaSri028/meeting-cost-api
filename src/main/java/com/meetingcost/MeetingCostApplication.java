package com.meetingcost;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication   // = @Configuration + @EnableAutoConfiguration + @ComponentScan
@EnableScheduling        // activates @Scheduled jobs (needed for calendar sync in Step 3)
public class MeetingCostApplication {

	public static void main(String[] args) {
		SpringApplication.run(MeetingCostApplication.class, args);
	}
}