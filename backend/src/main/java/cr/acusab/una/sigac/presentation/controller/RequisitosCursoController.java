package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.RequisitosCursoService;
import cr.acusab.una.sigac.presentation.dto.request.RequisitosCursoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.RequisitosCursoResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/requisitos-cursos")
public class RequisitosCursoController {

    private final RequisitosCursoService service;

    public RequisitosCursoController(RequisitosCursoService service) {
        this.service = service;
    }

    @GetMapping("/planes")
    public ResponseEntity<List<RequisitosCursoResponseDTO.PlanDTO>> listarPlanes() {
        return ResponseEntity.ok(service.listarPlanesVigentes());
    }

    @GetMapping("/cursos")
    public ResponseEntity<List<RequisitosCursoResponseDTO.CursoRequisitoDTO>> listarCursos(
            @RequestParam Long idPlanEstudio) {
        return ResponseEntity.ok(service.listarCursosDelPlan(idPlanEstudio));
    }

    @GetMapping
    public ResponseEntity<RequisitosCursoResponseDTO> consultar(
            @RequestParam Long idPlanEstudio,
            @RequestParam Long idCurso) {
        return ResponseEntity.ok(service.consultar(idPlanEstudio, idCurso));
    }

    @PutMapping
    public ResponseEntity<RequisitosCursoResponseDTO> guardar(
            @Valid @RequestBody RequisitosCursoRequestDTO request) {
        return ResponseEntity.ok(service.guardar(request));
    }
}
