package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.EstudianteService;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/estudiantes")
public class EstudianteController {

    private final EstudianteService estudianteService;

    public EstudianteController(EstudianteService estudianteService) {
        this.estudianteService = estudianteService;
    }

    @PostMapping
    public ResponseEntity<EstudianteResponse> registrar(@Valid @RequestBody EstudianteCreateRequest request) {
        EstudianteResponse response = estudianteService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<EstudianteResponse>> listar(@RequestParam(required = false) Boolean activo) {
        return ResponseEntity.ok(estudianteService.listar(activo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EstudianteResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(estudianteService.obtener(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EstudianteResponse> modificar(
            @PathVariable Long id, @Valid @RequestBody EstudianteUpdateRequest request) {
        return ResponseEntity.ok(estudianteService.modificar(id, request));
    }

    @PatchMapping("/{id}/baja")
    public ResponseEntity<EstudianteResponse> darDeBaja(@PathVariable Long id) {
        return ResponseEntity.ok(estudianteService.darDeBaja(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        estudianteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
