package com.meetingcost.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;           // the JWT — client stores this
    private String tokenType;       // always "Bearer"
    private String email;
    private String displayName;
    private long expiresIn;         // milliseconds until token expires
}