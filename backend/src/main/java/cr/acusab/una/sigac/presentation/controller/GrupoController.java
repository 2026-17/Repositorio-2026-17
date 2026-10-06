package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.logic.service.AsignacionProfesorService;
import cr.acusab.una.sigac.presentation.dto.request.AsignarProfesorRequest;
import cr.acusab.una.sigac.presentation.dto.response.AsignacionGrupoResponse;
import cr.acusab.una.sigac.presentation.dto.response.GrupoResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/grupos")
public class GrupoController {

    private final AsignacionProfesorService asignacionProfesorService;
    private final GrupoRepository grupoRepository;

    public GrupoController(AsignacionProfesorService asignacionProfesorService, GrupoRepository grupoRepository) {
        this.asignacionProfesorService = asignacionProfesorService;
        this.grupoRepository = grupoRepository;
    }

    /*
     * HU-014 - Soporta el flujo Curso -> Periodo -> Grupo al elegir
     * a quien asignar un profesor.
     */
    @GetMapping
    public ResponseEntity<List<GrupoResponse>> listar(
            @RequestParam Long idCurso, @RequestParam Long idPeriodo) {
        List<GrupoResponse> grupos = grupoRepository.findByIdCursoAndIdPeriodo(idCurso, idPeriodo).stream()
                .map(g -> new GrupoResponse(
                        g.getId(), g.getCodigo(), g.getIdCurso(), g.getIdPeriodo(),
                        g.getIdProfesor(), g.getCupoMaximo(), g.getModalidad(), g.getEstado()))
                .toList();
        return ResponseEntity.ok(grupos);
    }

    @GetMapping("/{idGrupo}/profesor")
    public ResponseEntity<AsignacionGrupoResponse> obtenerAsignacion(@PathVariable Long idGrupo) {
        return ResponseEntity.ok(asignacionProfesorService.obtenerAsignacion(idGrupo));
    }

    @PatchMapping("/{idGrupo}/profesor")
    public ResponseEntity<AsignacionGrupoResponse> asignarProfesor(
            @PathVariable Long idGrupo, @Valid @RequestBody AsignarProfesorRequest request) {
        return ResponseEntity.ok(asignacionProfesorService.asignar(idGrupo, request));
    }

    @DeleteMapping("/{idGrupo}/profesor")
    public ResponseEntity<Void> eliminarAsignacion(@PathVariable Long idGrupo) {
        asignacionProfesorService.eliminarAsignacion(idGrupo);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{idGrupo}/publicar")
    public ResponseEntity<AsignacionGrupoResponse> publicar(@PathVariable Long idGrupo) {
        return ResponseEntity.ok(asignacionProfesorService.publicar(idGrupo));
    }
}
