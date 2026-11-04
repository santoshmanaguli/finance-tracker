package in.santosh.finance.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import in.santosh.finance.common.FinanceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates machine-to-machine calls (the iOS Shortcut) with a static bearer-style token.
 *
 * Design notes:
 * <ul>
 *   <li><b>Filter, not controller code.</b> Auth is a cross-cutting concern: it runs before
 *       any controller, so a new endpoint under /api/ingest cannot "forget" to check it.</li>
 *   <li><b>Fail closed.</b> If no token is configured, every request is rejected. A missing env
 *       var in production must never mean "open to the internet".</li>
 *   <li><b>Constant-time comparison.</b> {@code String.equals} returns at the first differing
 *       character, so response timing leaks how much of a guess was right (a timing attack).
 *       {@link MessageDigest#isEqual} always compares every byte.</li>
 *   <li><b>No Spring Security (yet).</b> For one static token it would add a large framework
 *       for ~40 lines of logic. When the PWA needs a real login (Phase 4), we revisit.</li>
 * </ul>
 * Registered for {@code /api/ingest/*} in {@link SecurityConfig}.
 */
public class ApiTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Token";
    private static final Logger log = LoggerFactory.getLogger(ApiTokenFilter.class);

    private final FinanceProperties properties;

    public ApiTokenFilter(FinanceProperties properties) {
        this.properties = properties;
        if (!properties.ingestTokenConfigured()) {
            log.warn("INGEST_API_TOKEN is not set: all /api/ingest requests will be rejected");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (properties.ingestTokenConfigured() && matches(request.getHeader(HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        // Do not say whether the header was missing or wrong: no hints for an attacker.
        log.warn("Rejected ingest request: missing or invalid {} header from {}", HEADER, request.getRemoteAddr());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Unauthorized","status":401}""");
    }

    private boolean matches(String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                properties.ingestToken().getBytes(StandardCharsets.UTF_8));
    }
}
