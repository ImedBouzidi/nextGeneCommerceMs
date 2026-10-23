package tn.iteam.catalogueservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tn.iteam.catalogueservice.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleProductNotFound(ProductNotFoundException ex, HttpServletRequest request) {
        ErrorResponseDto error = new ErrorResponseDto(
                request.getRequestURI(),
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ProductAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDto> handleProductAlreadyExists(ProductAlreadyExistsException ex, HttpServletRequest request) {
        ErrorResponseDto error = new ErrorResponseDto(
                request.getRequestURI(),
                HttpStatus.CONFLICT,
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    /**
     * Bean-validation failures are the caller's fault, so they must be 400.
     * Left to the catch-all below they surfaced as 500, which reads like a server
     * bug and sends you hunting in the wrong place when a payload is simply invalid.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ErrorResponseDto error = new ErrorResponseDto(
                request.getRequestURI(),
                HttpStatus.BAD_REQUEST,
                details.isEmpty() ? "Validation failed" : details,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Raised by the static resource handler when an uploaded file is missing.
     * Without this the catch-all below reports a missing image as a 500, which
     * misleads both monitoring and anyone debugging a broken thumbnail.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    /**
     * Multipart files are rejected by the servlet container before the controller
     * runs. Without this handler the catch-all reports it as a 500, which hides the
     * real cause ("Maximum upload size exceeded") from the admin UI.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDto> handleMaxUploadSize(MaxUploadSizeExceededException ex,
                                                                HttpServletRequest request) {
        ErrorResponseDto error = new ErrorResponseDto(
                request.getRequestURI(),
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Uploaded file is too large. The maximum size per image is 10MB.",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneralException(Exception ex, HttpServletRequest request) {
        ErrorResponseDto error = new ErrorResponseDto(
                request.getRequestURI(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
