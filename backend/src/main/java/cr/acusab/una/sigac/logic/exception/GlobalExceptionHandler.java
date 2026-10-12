package cr.acusab.una.sigac.logic.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/*
 * Único manejador global de errores. Todas las respuestas de error tienen la
 * forma { status, message, fields? } para que el frontend siempre pueda
 * mostrar el mensaje específico. Los @ExceptionHandler de un controller
 * (por ejemplo MatriculaEstudianteController) tienen prioridad sobre estos.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> manejarBusinessException(
            BusinessException ex
    ) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> manejarDuplicateResourceException(
            DuplicateResourceException ex
    ) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> manejarResponseStatusException(
            ResponseStatusException ex
    ) {
        return respuesta(
                ex.getStatusCode(),
                ex.getReason() != null
                        ? ex.getReason()
                        : "La solicitud no pudo ser procesada.",
                null
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> manejarIllegalArgumentException(
            IllegalArgumentException ex
    ) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> manejarValidaciones(
            MethodArgumentNotValidException ex
    ) {

        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));

        String mensaje = fields.values()
                .stream()
                .findFirst()
                .orElse("Los datos enviados no son válidos.");

        return respuesta(HttpStatus.BAD_REQUEST, mensaje, fields);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> manejarParteFaltante(
            MissingServletRequestPartException ex
    ) {
        return respuesta(
                HttpStatus.BAD_REQUEST,
                "Falta la parte obligatoria del formulario: " + ex.getRequestPartName(),
                null
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> manejarJsonInvalido(
            HttpMessageNotReadableException ex
    ) {
        return respuesta(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido.", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> manejarArchivoMuyGrande(
            MaxUploadSizeExceededException ex
    ) {
        return respuesta(HttpStatus.BAD_REQUEST, "El archivo supera el tamaño máximo permitido.", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> manejarAutenticacion(
            AuthenticationException ex
    ) {

        String mensaje;
        if (ex instanceof LockedException) {
            mensaje = "La cuenta está bloqueada temporalmente por intentos fallidos. Intente más tarde.";
        } else if (ex instanceof DisabledException) {
            mensaje = "La cuenta está inactiva. Contacte a la administración.";
        } else if (ex instanceof BadCredentialsException || ex instanceof UsernameNotFoundException) {
            mensaje = "Cédula o contraseña incorrectas.";
        } else {
            mensaje = "No se pudo verificar la identidad del usuario.";
        }

        return respuesta(HttpStatus.UNAUTHORIZED, mensaje, null);
    }

    /*
     * Lo lanza @PreAuthorize cuando el rol no tiene permiso. Sin este
     * manejador caería en el genérico y llegaría como 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> manejarAccesoDenegado(
            AccessDeniedException ex
    ) {
        return respuesta(HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta acción.", null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> manejarIllegalStateException(
            IllegalStateException ex
    ) {
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> manejarExcepcionGeneral(
            Exception ex
    ) {

        // Errores propios de Spring MVC (404, 405, parámetro faltante...)
        // conservan su status en lugar de convertirse en 500.
        if (ex instanceof ErrorResponse errorResponse) {
            return respuesta(
                    errorResponse.getStatusCode(),
                    "La solicitud no pudo ser procesada.",
                    null
            );
        }

        return respuesta(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error interno al procesar la solicitud.",
                null
        );
    }

    private ResponseEntity<Map<String, Object>> respuesta(
            HttpStatusCode status,
            String mensaje,
            Map<String, String> fields
    ) {

        Map<String, Object> respuesta = new LinkedHashMap<>();

        respuesta.put("status", status.value());
        respuesta.put("message", mensaje);

        if (fields != null && !fields.isEmpty()) {
            respuesta.put("fields", fields);
        }

        return ResponseEntity
                .status(status)
                .body(respuesta);
    }
}
