package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.AgrupacionService;
import cr.acusab.una.sigac.presentation.dto.request.AgrupacionRequest;
import cr.acusab.una.sigac.presentation.dto.request.GrupoAgrupacionRequest;
import cr.acusab.una.sigac.presentation.dto.response.AgrupacionResponse;
import cr.acusab.una.sigac.presentation.dto.response.AvanceAgrupacionesResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteAgrupacionResponse;
import cr.acusab.una.sigac.presentation.dto.response.GrupoAgrupacionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
 * Agrupaciones (Orquesta, Banda, Coro de niños...).
 *
 * - Catálogo y grupos por período: crear y modificar DIRECTOR_ACADEMICO;
 *   consultar también ADMINISTRATIVO.
 * - Estudiantes de un grupo (con su nivel): dirección, administración y
 *   los profesores del grupo.
 * - Avance por agrupación de un estudiante: dirección y administración.
 * Las mismas reglas están en SecurityConfig.
 */
@RestController
@RequestMapping("/agrupaciones")
public class AgrupacionController {

    private final AgrupacionService agrupacionService;

    public AgrupacionController(AgrupacionService agrupacionService) {
        this.agrupacionService = agrupacionService;
    }

    /* ---------- Catálogo ---------- */

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR_ACADEMICO')")
    public ResponseEntity<List<AgrupacionResponse>> listar() {
        return ResponseEntity.ok(agrupacionService.listar());
    }

    @GetMapping("/{idAgrupacion}")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR_ACADEMICO')")
    public ResponseEntity<AgrupacionResponse> obtener(@PathVariable Long idAgrupacion) {
        return ResponseEntity.ok(agrupacionService.obtener(idAgrupacion));
    }

    @PostMapping
    @PreAuthorize("hasRole('DIRECTOR_ACADEMICO')")
    public ResponseEntity<AgrupacionResponse> crear(@Valid @RequestBody AgrupacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agrupacionService.crear(request));
    }

    @PutMapping("/{idAgrupacion}")
    @PreAuthorize("hasRole('DIRECTOR_ACADEMICO')")
    public ResponseEntity<AgrupacionResponse> actualizar(
            @PathVariable Long idAgrupacion, @Valid @RequestBody AgrupacionRequest request) {
        return ResponseEntity.ok(agrupacionService.actualizar(idAgrupacion, request));
    }

    /* ---------- Grupos por período ---------- */

    @GetMapping("/grupos")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR_ACADEMICO')")
    public ResponseEntity<List<GrupoAgrupacionResponse>> listarGrupos(@RequestParam(required = false) Long idPeriodo) {
        return ResponseEntity.ok(agrupacionService.listarGrupos(idPeriodo));
    }

    // Grupos del profesor de la sesión.
    @GetMapping("/grupos/mios")
    @PreAuthorize("hasRole('PROFESOR')")
    public ResponseEntity<List<GrupoAgrupacionResponse>> misGrupos(@RequestParam(required = false) Long idPeriodo) {
        return ResponseEntity.ok(agrupacionService.misGrupos(idPeriodo));
    }

    @GetMapping("/grupos/{idGrupo}")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR_ACADEMICO')")
    public ResponseEntity<GrupoAgrupacionResponse> obtenerGrupo(@PathVariable Long idGrupo) {
        return ResponseEntity.ok(agrupacionService.obtenerGrupo(idGrupo));
    }

    // Guardar como planificada o publicar. Los choques de horario vienen como aviso.
    @PostMapping("/grupos")
    @PreAuthorize("hasRole('DIRECTOR_ACADEMICO')")
    public ResponseEntity<GrupoAgrupacionResponse> crearGrupo(@Valid @RequestBody GrupoAgrupacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agrupacionService.crearGrupo(request));
    }

    @PutMapping("/grupos/{idGrupo}")
    @PreAuthorize("hasRole('DIRECTOR_ACADEMICO')")
    public ResponseEntity<GrupoAgrupacionResponse> actualizarGrupo(
            @PathVariable Long idGrupo, @Valid @RequestBody GrupoAgrupacionRequest request) {
        return ResponseEntity.ok(agrupacionService.actualizarGrupo(idGrupo, request));
    }

    // Revisar choques mientras se edita, sin guardar. idGrupo: el grupo que se edita (si ya existe).
    @PostMapping("/grupos/choques")
    @PreAuthorize("hasRole('DIRECTOR_ACADEMICO')")
    public ResponseEntity<List<GrupoAgrupacionResponse.Choque>> revisarChoques(
            @RequestParam(required = false) Long idGrupo, @RequestBody GrupoAgrupacionRequest request) {
        return ResponseEntity.ok(agrupacionService.revisarChoques(request, idGrupo));
    }

    // Vista interna: estudiantes con su nivel ("Orquesta · Conjunto Instrumental V").
    @GetMapping("/grupos/{idGrupo}/estudiantes")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR_ACADEMICO', 'PROFESOR')")
    public ResponseEntity<List<EstudianteAgrupacionResponse>> estudiantes(@PathVariable Long idGrupo) {
        return ResponseEntity.ok(agrupacionService.estudiantesDelGrupo(idGrupo));
    }

    /* ---------- Avance por agrupación ---------- */

    @GetMapping("/estudiantes/{idEstudiante}/avance")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR_ACADEMICO')")
    public ResponseEntity<AvanceAgrupacionesResponse> avance(@PathVariable Long idEstudiante) {
        return ResponseEntity.ok(agrupacionService.avanceDelEstudiante(idEstudiante));
    }
}
