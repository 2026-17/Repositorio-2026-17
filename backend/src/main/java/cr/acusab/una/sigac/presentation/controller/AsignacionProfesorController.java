package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.AsignacionProfesorService;
import cr.acusab.una.sigac.presentation.dto.request.AsignarProfesorGrupoRequest;
import cr.acusab.una.sigac.presentation.dto.response.AsignacionProfesorResponse;
import cr.acusab.una.sigac.presentation.dto.response.ProfesorAsignableResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/asignaciones-profesores")
@PreAuthorize("hasRole('DIRECTOR_ACADEMICO')")
public class AsignacionProfesorController {

    private final AsignacionProfesorService service;

    public AsignacionProfesorController(AsignacionProfesorService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<AsignacionProfesorResponse>> listar() {
        return ResponseEntity.ok(service.listarAsignaciones());
    }

    @GetMapping("/profesores-activos")
    public ResponseEntity<List<ProfesorAsignableResponse>> listarProfesoresActivos() {
        return ResponseEntity.ok(service.listarProfesoresActivos());
    }

    @PatchMapping("/grupos/{idGrupo}")
    public ResponseEntity<AsignacionProfesorResponse> asignar(
            @PathVariable Long idGrupo,
            @Valid @RequestBody AsignarProfesorGrupoRequest request
    ) {
        return ResponseEntity.ok(service.asignar(idGrupo, request.idProfesor()));
    }

    @DeleteMapping("/grupos/{idGrupo}")
    public ResponseEntity<Void> eliminar(@PathVariable Long idGrupo) {
        service.eliminarAsignacion(idGrupo);
        return ResponseEntity.noContent().build();
    }
}
