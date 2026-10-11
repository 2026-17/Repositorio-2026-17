package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.BajaEstudianteService;
import cr.acusab.una.sigac.presentation.dto.request.DarBajaEstudianteRequest;
import cr.acusab.una.sigac.presentation.dto.response.DarBajaEstudianteResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/estudiantes")
@PreAuthorize("hasRole('ADMINISTRATIVO')")
public class BajaEstudianteController {

    private final BajaEstudianteService bajaEstudianteService;

    public BajaEstudianteController(BajaEstudianteService bajaEstudianteService) {
        this.bajaEstudianteService = bajaEstudianteService;
    }

    @PatchMapping("/{id}/baja")
    public ResponseEntity<DarBajaEstudianteResponse> darDeBaja(
            @PathVariable Long id,
            @Valid @RequestBody DarBajaEstudianteRequest request
    ) {
        return ResponseEntity.ok(
                bajaEstudianteService.darDeBaja(id, request)
        );
    }
}
