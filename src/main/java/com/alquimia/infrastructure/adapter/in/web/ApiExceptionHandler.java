package com.alquimia.infrastructure.adapter.in.web;

import com.alquimia.domain.AlquimiaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AlquimiaException.class)
    public ProblemDetail domain(AlquimiaException error) {
        HttpStatus status = switch (error.code()) {
            case INVALID_DATA, UNKNOWN_DEVICE -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ALREADY_REGISTERED -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return problem(status, error.code().name(), error.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException error) {
        String detail = error.getBindingResult().getFieldErrors().stream()
            .map(field -> field.getField() + ": " + field.getDefaultMessage()).sorted()
            .collect(java.util.stream.Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "INVALID_DATA", detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail malformed() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_JSON", "El cuerpo JSON tiene campos o tipos invalidos.");
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
