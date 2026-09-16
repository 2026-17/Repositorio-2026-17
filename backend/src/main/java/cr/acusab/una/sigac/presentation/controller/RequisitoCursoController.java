package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.RequisitoCursoService;
import cr.acusab.una.sigac.presentation.dto.request.RequisitosCursoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.RequisitosCursoResponseDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/requisitos-cursos")
public class RequisitoCursoController {
    private final RequisitoCursoService service;

    public RequisitoCursoController(RequisitoCursoService service) { this.service = service; }

    @GetMapping("/planes")
    public ResponseEntity<?> planes() { return ResponseEntity.ok(service.listarPlanes()); }

    @GetMapping("/cursos")
    public ResponseEntity<?> cursos(@RequestParam @Positive Long idPlanEstudio) {
        return ResponseEntity.ok(service.listarCursos(idPlanEstudio));
    }

    @GetMapping
    public ResponseEntity<RequisitosCursoResponseDTO> consultar(
            @RequestParam @Positive Long idPlanEstudio,
            @RequestParam @Positive Long idCurso) {
        return ResponseEntity.ok(service.consultar(idPlanEstudio, idCurso));
    }

    @PutMapping
    public ResponseEntity<RequisitosCursoResponseDTO> configurar(
            @Valid @RequestBody RequisitosCursoRequestDTO request) {
        return ResponseEntity.ok(service.configurar(request));
    }
}
