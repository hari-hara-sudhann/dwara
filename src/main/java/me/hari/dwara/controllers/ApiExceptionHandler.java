package me.hari.dwara.controllers;

import me.hari.dwara.dtos.ResponseObject;
import me.hari.dwara.services.InvalidDeviceCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Keeps error responses in the same {@link ResponseObject} envelope the rest of
 * the API uses, instead of leaking Spring's default error page.
 *
 * <p>Business refusals are not exceptions in this codebase — services return a
 * failed {@code ResponseObject} with HTTP 200. This advice only covers request
 * shaping (bad JSON, bad path values) and authentication failures.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidDeviceCredentialsException.class)
    public ResponseEntity<ResponseObject<Void>> handleDeviceCredentials(
            InvalidDeviceCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ResponseObject.failure(exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseObject<Void>> handleUnreadableBody(
            HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(ResponseObject.failure("Malformed request body."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ResponseObject<Void>> handleResponseStatus(
            ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(ResponseObject.failure(exception.getReason()));
    }
}
