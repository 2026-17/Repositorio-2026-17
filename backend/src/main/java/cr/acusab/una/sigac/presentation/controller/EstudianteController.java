package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.EstudianteService;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteBusquedaResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteListadoResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/estudiantes")
@PreAuthorize("hasRole('ADMINISTRATIVO')")
public class EstudianteController {
    private final EstudianteService estudianteService;

    public EstudianteController(EstudianteService estudianteService) {
        this.estudianteService = estudianteService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EstudianteResponse> crear(
            @Valid @RequestPart("estudiante") EstudianteCreateRequest request,
            @RequestPart("politica") MultipartFile politica) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(estudianteService.crear(request, politica));
    }

    @GetMapping
    public ResponseEntity<List<EstudianteBusquedaResponse>> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Boolean activo) {
        return ResponseEntity.ok(estudianteService.buscar(texto));
    }

    @GetMapping("/listado")
    public ResponseEntity<List<EstudianteListadoResponse>> listado(
            @RequestParam(required = false) String texto) {
        return ResponseEntity.ok(estudianteService.listado(texto, activo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EstudianteResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(estudianteService.obtener(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EstudianteResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody EstudianteUpdateRequest request) {
        return ResponseEntity.ok(estudianteService.actualizar(id, request));
    }
}
