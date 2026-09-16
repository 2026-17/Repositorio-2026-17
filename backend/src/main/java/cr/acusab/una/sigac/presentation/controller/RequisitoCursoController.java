package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.RequisitoCursoService;
import cr.acusab.una.sigac.presentation.dto.request.RequisitosCursoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.RequisitosCursoResponseDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cursos")
public class RequisitoCursoController {

    private final RequisitoCursoService requisitoCursoService;

    public RequisitoCursoController(RequisitoCursoService requisitoCursoService) {
        this.requisitoCursoService = requisitoCursoService;
    }

    /**
     * HU-032 - Establecer prerrequisitos y correquisitos de un curso.
     */
    @PutMapping("/{idCurso}/requisitos")
    public ResponseEntity<RequisitosCursoResponseDTO> configurar(
            @PathVariable @Positive Long idCurso,
            @Valid @RequestBody RequisitosCursoRequestDTO request) {

        if (!idCurso.equals(request.getIdCurso())) {
            throw new IllegalArgumentException(
                    "El identificador del curso de la URL no coincide con el cuerpo de la solicitud."
            );
        }

        return ResponseEntity.ok(requisitoCursoService.configurar(request));
    }

    /**
     * HU-032 - Consultar la configuración actual del curso.
     */
    @GetMapping("/{idCurso}/requisitos")
    public ResponseEntity<RequisitosCursoResponseDTO> consultar(
            @PathVariable @Positive Long idCurso) {

        return ResponseEntity.ok(requisitoCursoService.consultar(idCurso));
    }
}
