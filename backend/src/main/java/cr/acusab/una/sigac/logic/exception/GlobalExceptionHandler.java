package cr.acusab.una.sigac.logic.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> manejarResponseStatusException(
            ResponseStatusException ex
    ) {

        Map<String, Object> respuesta = new LinkedHashMap<>();

        respuesta.put(
                "status",
                ex.getStatusCode().value()
        );

        respuesta.put(
                "message",
                ex.getReason() != null
                        ? ex.getReason()
                        : "La solicitud no pudo ser procesada."
        );

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(respuesta);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> manejarIllegalArgumentException(
            IllegalArgumentException ex
    ) {

        Map<String, Object> respuesta = new LinkedHashMap<>();

        respuesta.put(
                "status",
                HttpStatus.BAD_REQUEST.value()
        );

        respuesta.put(
                "message",
                ex.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(respuesta);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> manejarIllegalStateException(
            IllegalStateException ex
    ) {

        Map<String, Object> respuesta = new LinkedHashMap<>();

        respuesta.put(
                "status",
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );

        respuesta.put(
                "message",
                ex.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(respuesta);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> manejarValidaciones(
            MethodArgumentNotValidException ex
    ) {

        String mensaje = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Los datos enviados no son válidos.");

        Map<String, Object> respuesta = new LinkedHashMap<>();

        respuesta.put(
                "status",
                HttpStatus.BAD_REQUEST.value()
        );

        respuesta.put(
                "message",
                mensaje
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(respuesta);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> manejarJsonInvalido(
            HttpMessageNotReadableException ex
    ) {

        Map<String, Object> respuesta = new LinkedHashMap<>();

        respuesta.put(
                "status",
                HttpStatus.BAD_REQUEST.value()
        );

        respuesta.put(
                "message",
                "El cuerpo de la solicitud no es válido."
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(respuesta);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> manejarExcepcionGeneral(
            Exception ex
    ) {

        Map<String, Object> respuesta = new LinkedHashMap<>();

        respuesta.put(
                "status",
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );

        respuesta.put(
                "message",
                "Ocurrió un error interno al procesar la solicitud."
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(respuesta);
    }
}