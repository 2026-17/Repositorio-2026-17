package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.CursoService;
import cr.acusab.una.sigac.presentation.dto.request.CursoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.CursoListadoResponseDTO;
import cr.acusab.una.sigac.presentation.dto.response.CursoResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/cursos")
public class CursoController {

    private final CursoService cursoService;

    public CursoController(CursoService cursoService) {
        this.cursoService = cursoService;
    }

    /*
     * HU-028 - Crear curso en la malla curricular.
     */
    @PostMapping
    public ResponseEntity<CursoResponseDTO> crear(
            @Valid @RequestBody CursoRequestDTO request) {

        CursoResponseDTO creado = cursoService.crear(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(creado);
    }

    /*
     * HU-031 - Listado de cursos activos.
     *
     * Alimenta la pantalla de "Desactivar curso": permite buscar
     * y elegir el curso a desactivar en lugar de digitar su ID.
     */
    @GetMapping
    public ResponseEntity<List<CursoListadoResponseDTO>> listarActivos() {

        return ResponseEntity.ok(cursoService.listarActivos());
    }

    /*
     * HU-031 - Desactivar curso de la malla curricular.
     *
     * Se realiza una desactivación lógica (soft-delete).
     * El curso y su información histórica permanecen almacenados.
     */
    @PatchMapping("/{idCurso}/desactivar")
    public ResponseEntity<Map<String, String>> desactivar(
            @PathVariable Long idCurso) {

        cursoService.desactivar(idCurso);

        return ResponseEntity.ok(
                Map.of(
                        "mensaje",
                        "Curso desactivado correctamente."
                )
        );
    }
}