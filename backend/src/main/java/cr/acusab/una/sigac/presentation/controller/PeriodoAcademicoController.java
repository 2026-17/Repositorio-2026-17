package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.PeriodoAcademicoService;
import cr.acusab.una.sigac.presentation.dto.request.PeriodoAcademicoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.PeriodoAcademicoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/periodos-academicos")
public class PeriodoAcademicoController {

    private final PeriodoAcademicoService periodoAcademicoService;

    public PeriodoAcademicoController(
            PeriodoAcademicoService periodoAcademicoService
    ) {
        this.periodoAcademicoService = periodoAcademicoService;
    }

    /*
     * Creación de un período académico.
     */
    @PostMapping
    public ResponseEntity<PeriodoAcademicoResponse> crear(
            @Valid @RequestBody PeriodoAcademicoRequestDTO request) {

        PeriodoAcademicoResponse creado =
                periodoAcademicoService.crear(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(creado);
    }

    @GetMapping
    public List<PeriodoAcademicoResponse> listarPeriodosDisponibles() {
        return periodoAcademicoService.listarPeriodosDisponibles();
    }

    /*
     * Listado completo (incluye cerrados y cancelados), para la
     * pantalla de consulta/modificación del Director Académico.
     */
    @GetMapping("/todos")
    public List<PeriodoAcademicoResponse> listarTodos() {
        return periodoAcademicoService.listarTodos();
    }

    @GetMapping("/{idPeriodo}")
    public ResponseEntity<PeriodoAcademicoResponse> obtener(
            @PathVariable Long idPeriodo) {

        return ResponseEntity.ok(
                periodoAcademicoService.obtener(idPeriodo)
        );
    }

    /*
     * Modificación de un período académico existente.
     */
    @PutMapping("/{idPeriodo}")
    public ResponseEntity<PeriodoAcademicoResponse> actualizar(
            @PathVariable Long idPeriodo,
            @Valid @RequestBody PeriodoAcademicoRequestDTO request) {

        PeriodoAcademicoResponse actualizado =
                periodoAcademicoService.actualizar(idPeriodo, request);

        return ResponseEntity.ok(actualizado);
    }
}
