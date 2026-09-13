package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.EstudianteService;
import cr.acusab.una.sigac.presentation.dto.EstudianteCreateRequest;
import cr.acusab.una.sigac.presentation.dto.EstudianteResponse;
import cr.acusab.una.sigac.presentation.dto.EstudianteUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/estudiantes")
public class EstudianteController {
    private final EstudianteService estudianteService;

    public EstudianteController(EstudianteService estudianteService) { this.estudianteService = estudianteService; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstudianteResponse> crear(@Valid @RequestPart("estudiante") EstudianteCreateRequest request, @RequestPart("politica") MultipartFile politica) {
        return ResponseEntity.status(HttpStatus.CREATED).body(estudianteService.crear(request, politica));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstudianteResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(estudianteService.obtener(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<EstudianteResponse> actualizar(@PathVariable Long id, @Valid @RequestBody EstudianteUpdateRequest request) {
        return ResponseEntity.ok(estudianteService.actualizar(id, request));
    }
}
