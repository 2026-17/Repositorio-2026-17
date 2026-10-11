package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.ReactivarEstudianteService;
import cr.acusab.una.sigac.presentation.dto.response.ReactivarEstudianteResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/estudiantes")
@PreAuthorize("hasRole('ADMINISTRATIVO')")
public class ReactivarEstudianteController {

    private final ReactivarEstudianteService reactivarEstudianteService;

    public ReactivarEstudianteController(
            ReactivarEstudianteService reactivarEstudianteService
    ) {
        this.reactivarEstudianteService = reactivarEstudianteService;
    }

    @PatchMapping("/{id}/reactivar")
    public ResponseEntity<ReactivarEstudianteResponse> reactivar(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                reactivarEstudianteService.reactivar(id)
        );
    }
}
