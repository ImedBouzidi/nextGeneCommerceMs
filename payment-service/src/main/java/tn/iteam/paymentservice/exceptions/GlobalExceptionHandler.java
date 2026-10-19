package tn.iteam.paymentservice.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** BusinessException messages are authored by us and safe to show. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(Map.of("status", ex.getHttpStatus().value(),
                        "message", ex.getMessage()));
    }

    /**
     * Authorization failures are rejections, not server errors.
     *
     * <p>Without this handler {@code @PreAuthorize} denials fell through to the
     * catch-all below and came back as HTTP 500, so a client being correctly refused
     * an admin-only endpoint was indistinguishable from the service being broken.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", 403, "message", "You do not have permission to perform this action"));
    }

    /**
     * Unparseable or semantically invalid payload — most often an unknown
     * {@code paymentMethod} enum constant. Reported as 400 rather than 500 for the
     * same reason.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("status", 400, "message", "Invalid request payload"));
    }

    /**
     * Anything unexpected. The message is logged server-side but NOT returned: the
     * previous version echoed {@code ex.getMessage()} to the client, which leaks SQL
     * fragments, downstream URLs and internal identifiers on a payments endpoint.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        log.error("Unhandled error in payment-service", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", 500,
                        "message", "An unexpected error occurred. Please try again."));
    }
}