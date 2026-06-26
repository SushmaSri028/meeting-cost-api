package com.meetingcost.config;

import com.meetingcost.entity.User;
import com.meetingcost.repository.UserRepository;
import com.meetingcost.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;

        // 1. Get the OAuth2 user attributes from Google
        OAuth2User oauthUser = oauthToken.getPrincipal();
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();

        // Microsoft may use preferred_username instead of email
        String email = oauthUser.getAttribute("email");
        if (email == null) email = oauthUser.getAttribute("preferred_username");
        String name = oauthUser.getAttribute("name");
        if (name == null) name = email;
        // 2. Load the authorized client to get access + refresh tokens
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName()
        );

        String accessToken  = authorizedClient.getAccessToken().getTokenValue();
        String refreshToken = authorizedClient.getRefreshToken() != null
                ? authorizedClient.getRefreshToken().getTokenValue()
                : null;

        // 3. Find existing user or create new one
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // First time connecting Google — create account
            user = User.builder()
                    .email(email)
                    .displayName(name)
                    .passwordHash("")   // Google users don't have a local password
                    .build();
        }

        // 4. Save/update Google tokens
        // 4. Save tokens to the correct provider fields
        if ("microsoft".equalsIgnoreCase(registrationId)) {
            user.setMicrosoftAccessToken(accessToken);
            user.setMicrosoftRefreshToken(refreshToken);
            user.setMicrosoftTokenExpiry(
                    authorizedClient.getAccessToken().getExpiresAt() != null
                            ? OffsetDateTime.ofInstant(
                            authorizedClient.getAccessToken().getExpiresAt(),
                            java.time.ZoneOffset.UTC)
                            : OffsetDateTime.now().plusHours(1)
            );
        } else {
            user.setGoogleAccessToken(accessToken);
            user.setGoogleRefreshToken(refreshToken);
            user.setGoogleTokenExpiry(
                    authorizedClient.getAccessToken().getExpiresAt() != null
                            ? OffsetDateTime.ofInstant(
                            authorizedClient.getAccessToken().getExpiresAt(),
                            java.time.ZoneOffset.UTC)
                            : OffsetDateTime.now().plusHours(1)
            );
        }
        user.setProvider(registrationId.toUpperCase());

        userRepository.save(user);

        // 5. Generate our app's JWT for this user
        String jwt = jwtTokenProvider.generateToken(email);

        // 6. Redirect to frontend with JWT in URL
        // Frontend reads it from URL params and stores in memory/state
        String redirectUrl = frontendUrl + "/oauth2/callback?token=" + jwt
                + "&name=" + java.net.URLEncoder.encode(name != null ? name : "", "UTF-8")
                + "&email=" + java.net.URLEncoder.encode(email != null ? email : "", "UTF-8");
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}