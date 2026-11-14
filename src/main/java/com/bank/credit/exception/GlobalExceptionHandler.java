package com.bank.credit.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Captura validaciones de negocio (ej. "Un cliente personal solo puede tener un único crédito")
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Captura los errores traducidos por nuestro ReactiveFallbackHandler (Resilience4j)
    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        return buildErrorResponse(status != null ? status : HttpStatus.BAD_REQUEST, message);
    }

    // Captura errores generales no encontrados (ej. "Crédito no encontrado")
    @ExceptionHandler(RuntimeException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleRuntimeException(RuntimeException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Construye el formato JSON estándar para todas las respuestas de error
    private Mono<ResponseEntity<Map<String, Object>>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> errorBody = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message != null ? message : "Error procesando la solicitud"
        );
        return Mono.just(ResponseEntity.status(status).body(errorBody));
    }
}
