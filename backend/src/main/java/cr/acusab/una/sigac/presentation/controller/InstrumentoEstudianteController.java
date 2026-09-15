package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.InstrumentoEstudianteService;
import cr.acusab.una.sigac.presentation.dto.request.InstrumentoEstudianteRequest;
import cr.acusab.una.sigac.presentation.dto.response.InstrumentoEstudianteResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/*
 * HU-026 - Registrar instrumentos que aprende el estudiante.
 *
 * Se expone como sub-recurso de /api/estudiantes porque la HU
 * habla de "asociar el instrumento al expediente académico del
 * estudiante": el instrumento siempre se gestiona en contexto
 * de un estudiante puntual.
 *
 * Nota para el equipo: HU-001 (Registrar estudiante) ya crea la
 * primera asignación de instrumento como parte del alta del
 * estudiante (ver EstudianteService.crear). Este controller cubre
 * el resto de la HU-026: consultar el historial, agregar más
 * instrumentos y cambiar el instrumento principal manteniendo el
 * historial de asignaciones anteriores.
 */
@RestController
@RequestMapping("/api/estudiantes/{idEstudiante}/instrumentos")
public class InstrumentoEstudianteController {

    private final InstrumentoEstudianteService instrumentoEstudianteService;

    public InstrumentoEstudianteController(InstrumentoEstudianteService instrumentoEstudianteService) {
        this.instrumentoEstudianteService = instrumentoEstudianteService;
    }

    /*
     * HU-026 - Asignar un instrumento al estudiante.
     *
     * Por defecto queda como instrumento principal (cierra el
     * anterior si existía). Si se envía esPrincipal=false, se
     * agrega como instrumento adicional sin afectar al principal.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<InstrumentoEstudianteResponse> asignar(
            @PathVariable Long idEstudiante,
            @Valid @RequestBody InstrumentoEstudianteRequest request) {

        InstrumentoEstudianteResponse creado =
                instrumentoEstudianteService.asignar(idEstudiante, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    /*
     * HU-026 - Cambiar el instrumento principal del estudiante.
     *
     * Cierra la asignación principal vigente (fecha_fin = hoy) y
     * registra la nueva, conservando el historial completo.
     */
    @PutMapping("/principal")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<InstrumentoEstudianteResponse> cambiarPrincipal(
            @PathVariable Long idEstudiante,
            @Valid @RequestBody InstrumentoEstudianteRequest request) {

        InstrumentoEstudianteResponse actualizado =
                instrumentoEstudianteService.cambiarPrincipal(idEstudiante, request);

        return ResponseEntity.ok(actualizado);
    }

    /*
     * HU-026 - Consultar el historial completo de instrumentos
     * del estudiante (vigentes y cerrados), del más reciente al
     * más antiguo.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR_ACADEMICO')")
    public ResponseEntity<List<InstrumentoEstudianteResponse>> listarHistorial(
            @PathVariable Long idEstudiante) {

        return ResponseEntity.ok(instrumentoEstudianteService.listarHistorial(idEstudiante));
    }

    /*
     * HU-026 - Consultar el/los instrumento(s) que el estudiante
     * tiene asignados actualmente.
     */
    @GetMapping("/actual")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR_ACADEMICO')")
    public ResponseEntity<List<InstrumentoEstudianteResponse>> listarVigentes(
            @PathVariable Long idEstudiante) {

        return ResponseEntity.ok(instrumentoEstudianteService.listarVigentes(idEstudiante));
    }
}
