package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.exception.EspaciosInvalidosException;
import cr.acusab.una.sigac.logic.service.EspacioInstrumentoService;
import cr.acusab.una.sigac.presentation.dto.request.EspaciosInstrumentoRequest;
import cr.acusab.una.sigac.presentation.dto.request.HorarioGrupoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.EspacioInstrumentoResponse;
import cr.acusab.una.sigac.presentation.dto.response.InstrumentoProfesorResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Espacios de instrumento: lecciones individuales semanales de cada
 * profesor (grupos ESPACIO_INSTRUMENTO, cupo 1, sin nivel).
 *
 * - Crear, modificar y eliminar: DIRECTOR_ACADEMICO.
 * - Consultar la agenda: ADMINISTRATIVO y DIRECTOR_ACADEMICO.
 * Las mismas reglas están en SecurityConfig.
 */
@RestController
@RequestMapping("/espacios-instrumento")
public class EspacioInstrumentoController {

    private final EspacioInstrumentoService espacioInstrumentoService;

    public EspacioInstrumentoController(EspacioInstrumentoService espacioInstrumentoService) {
        this.espacioInstrumentoService = espacioInstrumentoService;
    }

    // Crear en lote (todo o nada).
    @PostMapping
    @PreAuthorize("hasRole('DIRECTOR_ACADEMICO')")
    public ResponseEntity<List<EspacioInstrumentoResponse>> crear(
            @Valid @RequestBody EspaciosInstrumentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(espacioInstrumentoService.crear(request));
    }

    // Agenda: espacios con su estado (libre u ocupado con estudiante y nivel).
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR_ACADEMICO')")
    public ResponseEntity<List<EspacioInstrumentoResponse>> listar(
            @RequestParam(required = false) Long idPeriodo,
            @RequestParam(required = false) Integer idInstrumento,
            @RequestParam(required = false) Long idProfesor) {
        return ResponseEntity.ok(espacioInstrumentoService.listar(idPeriodo, idInstrumento, idProfesor));
    }

    // Instrumentos activos que da un profesor (para ofrecer "Cualquiera de sus instrumentos").
    @GetMapping("/profesores/{idProfesor}/instrumentos")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR_ACADEMICO')")
    public ResponseEntity<List<InstrumentoProfesorResponse>> instrumentosDelProfesor(@PathVariable Long idProfesor) {
        return ResponseEntity.ok(espacioInstrumentoService.instrumentosDelProfesor(idProfesor));
    }

    // Modificar el día, las horas o el aula de un espacio libre.
    @PutMapping("/{idGrupo}")
    @PreAuthorize("hasRole('DIRECTOR_ACADEMICO')")
    public ResponseEntity<EspacioInstrumentoResponse> actualizar(
            @PathVariable Long idGrupo,
            @Valid @RequestBody HorarioGrupoRequestDTO request) {
        return ResponseEntity.ok(espacioInstrumentoService.actualizar(idGrupo, request));
    }

    // Eliminar un espacio libre y sin historial.
    @DeleteMapping("/{idGrupo}")
    @PreAuthorize("hasRole('DIRECTOR_ACADEMICO')")
    public ResponseEntity<Void> eliminar(@PathVariable Long idGrupo) {
        espacioInstrumentoService.eliminar(idGrupo);
        return ResponseEntity.noContent().build();
    }

    /*
     * Errores por bloque: { status, message, errores: [{ indice, mensaje }] }.
     * indice es la posición del bloque en la lista enviada (desde 0).
     */
    @ExceptionHandler(EspaciosInvalidosException.class)
    public ResponseEntity<Map<String, Object>> espaciosInvalidos(EspaciosInvalidosException ex) {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("status", HttpStatus.BAD_REQUEST.value());
        respuesta.put("message", ex.getMessage());
        respuesta.put("errores", ex.getErrores());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(respuesta);
    }
}
