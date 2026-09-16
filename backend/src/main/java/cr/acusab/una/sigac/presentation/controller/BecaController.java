package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.BecaService;
import cr.acusab.una.sigac.presentation.dto.request.BecaActualizarRequest;
import cr.acusab.una.sigac.presentation.dto.request.BecaAsignarRequest;
import cr.acusab.una.sigac.presentation.dto.response.BecaResponse;
import cr.acusab.una.sigac.presentation.dto.response.TipoBecaResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/becas")
public class BecaController {

    private final BecaService becaService;

    public BecaController(BecaService becaService) {
        this.becaService = becaService;
    }

    /*
     * HU-015 - Asignar una beca a un estudiante.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<BecaResponse> asignar(@Valid @RequestBody BecaAsignarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(becaService.asignar(request));
    }

    /*
     * HU-015 - Catálogo de tipos de beca disponibles.
     */
    @GetMapping("/tipos-becas")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<TipoBecaResponse>> tiposBecas() {
        return ResponseEntity.ok(becaService.listarTiposBeca());
    }

    /*
     * HU-015 - Obtener el detalle de una beca asignada.
     */
    @GetMapping("/{idBeca}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<BecaResponse> obtener(@PathVariable Long idBeca) {
        return ResponseEntity.ok(becaService.obtener(idBeca));
    }

    /*
     * HU-015 - Listar las becas de un estudiante (incluye el porcentaje
     * de beca aplicado y el monto actualizado a pagar).
     */
    @GetMapping("/estudiante/{idEstudiante}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<BecaResponse>> listarPorEstudiante(@PathVariable Long idEstudiante) {
        return ResponseEntity.ok(becaService.listarPorEstudiante(idEstudiante));
    }

    /*
     * HU-015 - Modificar una beca previamente asignada.
     */
    @PutMapping("/{idBeca}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<BecaResponse> actualizar(
            @PathVariable Long idBeca,
            @Valid @RequestBody BecaActualizarRequest request) {
        return ResponseEntity.ok(becaService.actualizar(idBeca, request));
    }

    /*
     * HU-015 - Eliminar una beca asignada a un estudiante.
     */
    @DeleteMapping("/{idBeca}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminar(@PathVariable Long idBeca) {
        becaService.eliminar(idBeca);
        return ResponseEntity.noContent().build();
    }
}
