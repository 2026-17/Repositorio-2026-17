package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.exception.MatriculaInvalidaException;
import cr.acusab.una.sigac.logic.service.MatriculaEstudianteService;
import cr.acusab.una.sigac.presentation.dto.request.ConfirmarSeleccionMatriculaRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.MatriculaConfirmadaResponseDTO;
import cr.acusab.una.sigac.presentation.dto.response.OfertaMatriculaResponseDTO;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/*
 * Historia 2 - Matricular cursos previamente autorizados.
 *
 * Rol: ESTUDIANTE. El estudiante siempre se obtiene de la sesión activa.
 */
@RestController
@RequestMapping("/matriculas-estudiante")
public class MatriculaEstudianteController {

    private final MatriculaEstudianteService matriculaEstudianteService;

    public MatriculaEstudianteController(
            MatriculaEstudianteService matriculaEstudianteService
    ) {
        this.matriculaEstudianteService =
                matriculaEstudianteService;
    }

    /*
     * Consultar si el estudiante está habilitado para matricular y, en
     * ese caso, los grupos disponibles del período vigente.
     */
    @GetMapping("/mi-oferta")
    public ResponseEntity<OfertaMatriculaResponseDTO> consultarMiOferta() {

        return ResponseEntity.ok(
                matriculaEstudianteService
                        .consultarMiOferta()
        );
    }

    /*
     * Confirmar la selección: valida las reglas de matrícula y, si se
     * cumplen, registra la matrícula y descuenta los cupos.
     */
    @PostMapping("/confirmar")
    public ResponseEntity<MatriculaConfirmadaResponseDTO> confirmar(
            @Valid @RequestBody ConfirmarSeleccionMatriculaRequestDTO request
    ) {

        return ResponseEntity.ok(
                matriculaEstudianteService
                        .confirmar(request)
        );
    }

    /*
     * Los manejadores de errores del controller tienen prioridad sobre los
     * globales. Garantizan que las reglas de matrícula incumplidas lleguen
     * al estudiante como 400 con su motivo, en lugar de un error genérico.
     */
    @ExceptionHandler(MatriculaInvalidaException.class)
    public ResponseEntity<Map<String, Object>> matriculaInvalida(
            MatriculaInvalidaException ex
    ) {

        Map<String, Object> respuesta = new LinkedHashMap<>();

        respuesta.put("status", HttpStatus.BAD_REQUEST.value());
        respuesta.put("message", ex.getMessage());
        respuesta.put("errores", ex.getErrores());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(respuesta);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> reglaDeNegocio(
            BusinessException ex
    ) {

        Map<String, Object> respuesta = new LinkedHashMap<>();

        respuesta.put("status", HttpStatus.BAD_REQUEST.value());
        respuesta.put("message", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(respuesta);
    }
}
