package com.meetingcost.security;

import com.meetingcost.entity.User;
import com.meetingcost.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email;
        String name;

        if ("microsoft".equalsIgnoreCase(registrationId)) {
            email = extractMicrosoftEmail(attributes);
            name  = (String) attributes.getOrDefault("name", email);
        } else {
            email = (String) attributes.getOrDefault("email", "");
            name  = (String) attributes.getOrDefault("name", email);
        }

        Optional<User> existing = userRepository.findByEmail(email);
        User user;
        if (existing.isPresent()) {
            user = existing.get();
            user.setDisplayName(name);
            user.setProvider(registrationId.toUpperCase());
        } else {
            user = User.builder()
                    .email(email)
                    .displayName(name)
                    .provider(registrationId.toUpperCase())
                    .passwordHash("OAUTH2_USER")
                    .build();
        }
        userRepository.save(user);

        return oAuth2User;
    }

    private String extractMicrosoftEmail(Map<String, Object> attributes) {
        if (attributes.get("email") != null) return (String) attributes.get("email");
        if (attributes.get("preferred_username") != null) return (String) attributes.get("preferred_username");
        return attributes.getOrDefault("sub", "unknown").toString();
    }
}