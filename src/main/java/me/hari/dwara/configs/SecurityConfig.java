package me.hari.dwara.configs;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * Security posture for spec §5/D13:
 *
 * <ul>
 *   <li>{@code /auth/**} is public.</li>
 *   <li>Administration endpoints are allowed through the chain but are guarded
 *       by {@link AdminKeyFilter} before authorization runs.</li>
 *   <li>{@code /gate/validate} and {@code /devices/{id}/commands} are the
 *       ESP32's device-secret surface; the controller verifies the secret
 *       (the device id lives in the body/path, so it is not a good filter
 *       concern).</li>
 *   <li>Everything else requires a valid JWT.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String UNAUTHORIZED_BODY =
            "{\"success\":false,\"message\":\"Authentication required.\",\"data\":null}";
    private static final String FORBIDDEN_BODY =
            "{\"success\":false,\"message\":\"Access denied.\",\"data\":null}";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AdminKeyFilter adminKeyFilter;

    @Bean
    public PasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) ->
                                writeJson(response, HttpStatus.UNAUTHORIZED, UNAUTHORIZED_BODY))
                        .accessDeniedHandler((request, response, ex) ->
                                writeJson(response, HttpStatus.FORBIDDEN, FORBIDDEN_BODY)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/error").permitAll()
                        // ESP32 device-secret endpoints (validated in the controller).
                        .requestMatchers(HttpMethod.POST, "/gate/validate").permitAll()
                        .requestMatchers(HttpMethod.GET, "/devices/*/commands").permitAll()
                        // Admin-key endpoints (guarded by AdminKeyFilter).
                        .requestMatchers(HttpMethod.POST, "/facilities", "/devices").permitAll()
                        .requestMatchers(HttpMethod.POST, "/devices/*/commands").permitAll()
                        .requestMatchers(HttpMethod.PUT,
                                "/facilities/*/pricing", "/facilities/*/status").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(adminKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void writeJson(HttpServletResponse response, HttpStatus status, String body)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }
}
