package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.EstudianteService;
import cr.acusab.una.sigac.presentation.dto.response.TipoEstudianteResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/*
 * Catálogo de tipos de estudiante (REGULAR / INCLUSION).
 *
 * Los roles con acceso (ADMINISTRATIVO y DIRECTOR_ACADEMICO) se definen en
 * SecurityConfig.
 */
@RestController
@RequestMapping("/tipos-estudiante")
public class TipoEstudianteController {

    private final EstudianteService estudianteService;

    public TipoEstudianteController(EstudianteService estudianteService) {
        this.estudianteService = estudianteService;
    }

    /*
     * Tipos activos: opciones del selector "Tipo de estudiante" del registro,
     * la edición y el filtro del listado de estudiantes.
     */
    @GetMapping
    public ResponseEntity<List<TipoEstudianteResponse>> listarActivos() {
        return ResponseEntity.ok(estudianteService.listarTiposEstudiante());
    }
}
