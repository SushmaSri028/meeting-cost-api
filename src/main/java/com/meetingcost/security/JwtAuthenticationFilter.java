package com.meetingcost.security;

import com.meetingcost.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

// OncePerRequestFilter guarantees this filter runs exactly once per HTTP request
@Component
@RequiredArgsConstructor   // Lombok: generates constructor for all final fields (= @Autowired)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Step 1: Extract token from Authorization header
        String token = extractTokenFromRequest(request);

        // Step 2: If token exists and is valid, authenticate the user
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {

            // Step 3: Get email from token
            String email = jwtTokenProvider.getEmailFromToken(token);

            // Step 4: Verify user still exists in the database
            userRepository.findByEmail(email).ifPresent(user -> {

                // Step 5: Create Spring Security authentication object
                UserDetails userDetails = User.builder()
                        .username(user.getEmail())
                        .password("")                // password not needed here
                        .authorities(Collections.emptyList())
                        .build();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Step 6: Set authentication in Spring Security context
                // This is what makes the current request "authenticated"
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        // Step 7: Continue the filter chain regardless
        // If not authenticated, Spring Security will return 401 for protected routes
        filterChain.doFilter(request, response);
    }

    // Reads the "Authorization: Bearer <token>" header and returns just the token part
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);   // remove "Bearer " prefix (7 characters)
        }

        return null;
    }
}