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

/*
 * HU-026 - Registrar instrumentos que aprende el estudiante.
 *
 * Se expone como sub-recurso de /estudiantes porque la HU
 * habla de "asociar el instrumento al expediente académico del
 * estudiante": el instrumento siempre se gestiona en contexto
 * de un estudiante puntual.
 *
 * Un estudiante puede tener varios instrumentos vigentes a la vez;
 * no existe el concepto de instrumento "principal". Un instrumento
 * vigente se puede deshabilitar (finalizar), lo que registra su
 * fecha de fin y lo conserva en el historial.
 */
@RestController
@RequestMapping("/estudiantes/{idEstudiante}/instrumentos")
public class InstrumentoEstudianteController {

    private final InstrumentoEstudianteService instrumentoEstudianteService;

    public InstrumentoEstudianteController(InstrumentoEstudianteService instrumentoEstudianteService) {
        this.instrumentoEstudianteService = instrumentoEstudianteService;
    }

    /*
     * HU-026 - Asignar un instrumento al estudiante.
     *
     * Se agrega como instrumento vigente adicional; el estudiante
     * puede tener varios instrumentos vigentes al mismo tiempo.
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
     * HU-026 - Deshabilitar (finalizar) una asignación de
     * instrumento vigente del estudiante.
     *
     * Registra fecha_fin con la fecha del día en que se
     * deshabilita y conserva el registro en el historial.
     */
    @PutMapping("/{idEstudianteInstrumento}/deshabilitar")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<InstrumentoEstudianteResponse> deshabilitar(
            @PathVariable Long idEstudiante,
            @PathVariable Long idEstudianteInstrumento) {

        InstrumentoEstudianteResponse actualizado =
                instrumentoEstudianteService.deshabilitar(idEstudiante, idEstudianteInstrumento);

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
