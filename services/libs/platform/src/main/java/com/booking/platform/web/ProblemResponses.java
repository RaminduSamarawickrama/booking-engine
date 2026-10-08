package com.booking.platform.web;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 problem response with a stable {@code code} and the
 * {@code requestId}, so a customer-facing message can always be traced to the server logs.
 * Unexpected exceptions are logged in full but never leak details to the caller.
 */
@RestControllerAdvice
public class ProblemResponses extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemResponses.class);
    private static final String TYPE_PREFIX = "urn:booking:problem:";

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex) {
        return problem(ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "You do not have permission to do this.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Sign in to continue.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                "Something went wrong on our side. Quote the request id if you contact support.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "validation_failed", "Some fields are invalid.");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().headers(headers).body(body);
    }

    /** Framework exceptions (bad JSON, wrong method, ...) keep Spring's status but gain our fields. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
            WebRequest request) {
        if (body instanceof ProblemDetail problem && problem.getProperties() == null) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            String code = status == null ? "error" : status.name().toLowerCase(java.util.Locale.ROOT);
            problem.setType(URI.create(TYPE_PREFIX + code));
            problem.setProperty("code", code);
            RequestIds.current().ifPresent(id -> problem.setProperty("requestId", id));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    public static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setProperty("code", code);
        RequestIds.current().ifPresent(id -> problem.setProperty("requestId", id));
        return problem;
    }
}
