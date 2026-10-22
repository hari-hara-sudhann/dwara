package me.hari.dwara.configs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Static admin-key guard for the small administration surface of spec §5/D13:
 * creating facilities and devices, changing pricing/status, and queueing a
 * remote gate command.
 *
 * <p>The key comes from {@code dwara.admin-key} (env {@code DWARA_ADMIN_KEY}).
 * There is deliberately no ADMIN user role in v1 — this header is the whole
 * admin identity. When the property is blank the admin surface is closed.
 */
@Component
public class AdminKeyFilter extends OncePerRequestFilter {

    public static final String ADMIN_KEY_HEADER = "X-Admin-Key";

    private static final Pattern FACILITY_ADMIN_PATH =
            Pattern.compile("^/facilities/[^/]+/(pricing|status)$");
    private static final Pattern DEVICE_COMMAND_PATH =
            Pattern.compile("^/devices/[^/]+/commands$");

    private static final String UNAUTHORIZED_BODY =
            "{\"success\":false,\"message\":\"A valid X-Admin-Key header is required.\",\"data\":null}";

    private final String adminKey;

    public AdminKeyFilter(@Value("${dwara.admin-key:}") String adminKey) {
        this.adminKey = adminKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!isAdminRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(ADMIN_KEY_HEADER);

        if (!keyMatches(provided)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(UNAUTHORIZED_BODY);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean keyMatches(String provided) {
        if (adminKey == null || adminKey.isBlank() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                adminKey.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The admin surface is defined by method + path, not by a URL prefix, so
     * {@code GET /facilities/{code}} stays a normal JWT endpoint while
     * {@code PUT /facilities/{code}/pricing} requires the admin key.
     */
    private static boolean isAdminRequest(HttpServletRequest request) {
        String path = request.getRequestURI()
                .substring(request.getContextPath().length());
        String method = request.getMethod();

        return switch (method) {
            case "POST" -> path.equals("/facilities")
                    || path.equals("/devices")
                    || DEVICE_COMMAND_PATH.matcher(path).matches();
            case "PUT" -> FACILITY_ADMIN_PATH.matcher(path).matches();
            default -> false;
        };
    }
}
