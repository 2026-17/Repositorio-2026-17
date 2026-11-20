package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.AvanceEstudianteService;
import cr.acusab.una.sigac.presentation.dto.request.NivelIngresoRequest;
import cr.acusab.una.sigac.presentation.dto.response.AvanceInstrumentosResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/*
 * Avance por instrumento del estudiante y nivel de ingreso (prueba de
 * ubicación). Las mismas reglas de rol están en SecurityConfig.
 */
@RestController
@RequestMapping("/estudiantes/{idEstudiante}")
public class AvanceInstrumentoController {

    private final AvanceEstudianteService avanceEstudianteService;

    public AvanceInstrumentoController(AvanceEstudianteService avanceEstudianteService) {
        this.avanceEstudianteService = avanceEstudianteService;
    }

    // Niveles I..XIV de cada instrumento vigente con su estado.
    @GetMapping("/avance-instrumentos")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR_ACADEMICO')")
    public ResponseEntity<AvanceInstrumentosResponse> consultar(@PathVariable Long idEstudiante) {
        return ResponseEntity.ok(avanceEstudianteService.consultar(idEstudiante));
    }

    // Registrar el nivel de ingreso: los niveles anteriores quedan convalidados.
    @PostMapping("/instrumentos/{idInstrumento}/nivel-ingreso")
    @PreAuthorize("hasRole('DIRECTOR_ACADEMICO')")
    public ResponseEntity<AvanceInstrumentosResponse> registrarNivelIngreso(
            @PathVariable Long idEstudiante,
            @PathVariable Integer idInstrumento,
            @Valid @RequestBody NivelIngresoRequest request) {
        return ResponseEntity.ok(
                avanceEstudianteService.registrarNivelIngreso(idEstudiante, idInstrumento, request));
    }
}
