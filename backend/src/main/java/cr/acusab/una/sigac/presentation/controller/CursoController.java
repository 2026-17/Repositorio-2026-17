package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.CursoService;
import cr.acusab.una.sigac.presentation.dto.request.CursoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.request.ModificarCursoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.CursoEdicionResponseDTO;
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

        CursoResponseDTO creado =
                cursoService.crear(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(creado);
    }

    /*
     * HU-031 - Listado de cursos activos.
     *
     * Alimenta las pantallas de gestión de cursos y permite
     * buscar/seleccionar un curso activo.
     */
    @GetMapping
    public ResponseEntity<List<CursoListadoResponseDTO>> listarActivos() {

        return ResponseEntity.ok(
                cursoService.listarActivos()
        );
    }

    /*
     * HU-030 - Obtener los datos actuales de un curso.
     *
     * Se utiliza para precargar el formulario de modificación
     * con la información actual del curso, su programa y nivel.
     */
    @GetMapping("/{idCurso}")
    public ResponseEntity<CursoEdicionResponseDTO> obtenerParaEdicion(
            @PathVariable Long idCurso) {

        CursoEdicionResponseDTO curso =
                cursoService.obtenerParaEdicion(idCurso);

        return ResponseEntity.ok(curso);
    }

    /*
     * HU-030 - Modificar curso de la malla curricular.
     *
     * Permite modificar:
     * - nombre;
     * - descripción;
     * - cantidad de horas;
     * - programa académico;
     * - nivel académico.
     *
     * El código del curso y la información histórica
     * no se modifican.
     */
    @PutMapping("/{idCurso}")
    public ResponseEntity<CursoEdicionResponseDTO> modificar(
            @PathVariable Long idCurso,
            @Valid @RequestBody ModificarCursoRequestDTO request) {

        CursoEdicionResponseDTO actualizado =
                cursoService.modificar(
                        idCurso,
                        request
                );

        return ResponseEntity.ok(actualizado);
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
