package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.ProfesorService;
import cr.acusab.una.sigac.presentation.dto.request.ProfesorCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.ProfesorUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.request.UsuarioCreateRequest;
import cr.acusab.una.sigac.presentation.dto.response.ProfesorResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/profesores")
public class ProfesorController {

    private final ProfesorService profesorService;

    public ProfesorController(ProfesorService profesorService) {
        this.profesorService = profesorService;
    }

    @PostMapping
    public ResponseEntity<ProfesorResponse> registrar(@Valid @RequestBody ProfesorCreateRequest request, @Valid @RequestBody UsuarioCreateRequest usuarioRequest) {
        ProfesorResponse response = profesorService.registrar(request, usuarioRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProfesorResponse>> listar(@RequestParam(required = false) Boolean activo) {
        return ResponseEntity.ok(profesorService.listar(activo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfesorResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(profesorService.obtener(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProfesorResponse> modificar(
            @PathVariable Long id, @Valid @RequestBody ProfesorUpdateRequest request) {
        return ResponseEntity.ok(profesorService.modificar(id, request));
    }

    @PatchMapping("/{id}/baja")
    public ResponseEntity<ProfesorResponse> darDeBaja(@PathVariable Long id) {
        return ResponseEntity.ok(profesorService.darDeBaja(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        profesorService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
