package in.santosh.finance.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * One place that turns exceptions into HTTP responses, using RFC 9457 "Problem Details"
 * ({@code application/problem+json}): a standard error shape clients can rely on.
 *
 * Extending {@link ResponseEntityExceptionHandler} gives correct handling of Spring MVC's own
 * errors for free (400 for invalid JSON or failed {@code @Valid}, 405, 415, ...).
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * A unique constraint fired. In this app that almost always means two identical requests
     * raced past the "already exists?" check (e.g. the Shortcut fired twice at the same moment).
     * The first one won and its data is safe, so 409 is accurate and harmless.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConflict(DataIntegrityViolationException ex) {
        log.info("Constraint violation (likely concurrent duplicate): {}", ex.getMostSpecificCause().getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Conflicting or duplicate data");
    }
}
