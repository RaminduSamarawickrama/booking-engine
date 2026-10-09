package com.booking.platform.error;

import org.springframework.http.HttpStatus;

/**
 * An error a service deliberately returns to its caller, thrown from any layer (domain rules
 * included) and turned into a problem response by the web layer. The {@code code} is a stable,
 * machine-readable identifier (for example {@code booking_not_found}) that clients can switch
 * on; the message is a human-readable detail and may change.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        if (!code.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Error codes are snake_case: " + code);
        }
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException unprocessable(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }
}
