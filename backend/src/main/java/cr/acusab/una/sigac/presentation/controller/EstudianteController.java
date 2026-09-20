package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.EstudianteService;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteCreateRequest;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteBusquedaResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteResponse;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteUpdateRequest;
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
public class EstudianteController {
    private final EstudianteService estudianteService;

    public EstudianteController(EstudianteService estudianteService) { this.estudianteService = estudianteService; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<EstudianteResponse> crear(@Valid @RequestPart("estudiante") EstudianteCreateRequest request, @RequestPart("politica") MultipartFile politica) {
        return ResponseEntity.status(HttpStatus.CREATED).body(estudianteService.crear(request, politica));
    }

    /*
     * Busqueda de estudiantes por nombre o cedula, usada en Administracion
     * Academica para ubicar al estudiante antes de ver su beca o pagos.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR_ACADEMICO')")
    public ResponseEntity<List<EstudianteBusquedaResponse>> buscar(@RequestParam(required = false) String texto) {
        return ResponseEntity.ok(estudianteService.buscar(texto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<EstudianteResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(estudianteService.obtener(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ResponseEntity<EstudianteResponse> actualizar(@PathVariable Long id, @Valid @RequestBody EstudianteUpdateRequest request) {
        return ResponseEntity.ok(estudianteService.actualizar(id, request));
    }
}
