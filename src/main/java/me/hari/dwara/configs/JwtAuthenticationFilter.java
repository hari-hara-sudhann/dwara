package me.hari.dwara.configs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import me.hari.dwara.utilities.JwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Turns {@code Authorization: Bearer <jwt>} into an authenticated principal
 * whose name is the user id (spec §5: all app endpoints are JWT guarded, D13
 * keeps the role model to {@code USER} only).
 *
 * <p>Invalid or expired tokens are left unauthenticated rather than rejected
 * here: the security chain's authorization rules decide whether the endpoint
 * needed a user at all. Device endpoints also use a bearer header, but their
 * value is a plain device secret, so only JWT-shaped tokens (three dot-separated
 * parts) are parsed in this filter.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = header.substring(BEARER_PREFIX.length()).trim();

            if (isJwtShaped(token)) {
                try {
                    UUID userId = jwtService.extractUserId(token);
                    if (!jwtService.isTokenExpired(token)) {
                        var authentication = new UsernamePasswordAuthenticationToken(
                                userId, null, List.of());
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (RuntimeException ignored) {
                    // Malformed/forged token: stay anonymous and let the
                    // authorization rules return 401 where a user is required.
                    SecurityContextHolder.clearContext();
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isJwtShaped(String token) {
        int dots = 0;
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) == '.') {
                dots++;
            }
        }
        return dots == 2;
    }
}
