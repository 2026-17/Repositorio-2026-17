package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.InscripcionPlanEstudioService;
import cr.acusab.una.sigac.presentation.dto.response.PlanEstudioOpcionResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/*
 * Los roles con acceso (ADMINISTRATIVO y DIRECTOR_ACADEMICO) se definen en
 * SecurityConfig.
 */
@RestController
@RequestMapping("/planes-estudio")
public class PlanEstudioController {

    private final InscripcionPlanEstudioService inscripcionPlanEstudioService;

    public PlanEstudioController(InscripcionPlanEstudioService inscripcionPlanEstudioService) {
        this.inscripcionPlanEstudioService = inscripcionPlanEstudioService;
    }

    /*
     * Planes de estudio vigentes: opciones del selector "Planes de estudio"
     * del registro y la edición de estudiantes.
     */
    @GetMapping
    public ResponseEntity<List<PlanEstudioOpcionResponse>> listarVigentes() {
        return ResponseEntity.ok(inscripcionPlanEstudioService.listarPlanesVigentes());
    }
}
