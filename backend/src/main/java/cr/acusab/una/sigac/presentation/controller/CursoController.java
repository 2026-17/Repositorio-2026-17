package cr.acusab.una.sigac.presentation.controller;


import cr.acusab.una.sigac.logic.service.CursoService;
import cr.acusab.una.sigac.presentation.dto.response.CursoResponseDTO;
import cr.acusab.una.sigac.presentation.dto.request.CursoRequestDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cursos")
public class CursoController {

    private final CursoService cursoService;

    public CursoController(CursoService cursoService) {
        this.cursoService = cursoService;
    }

    @PostMapping
    public ResponseEntity<CursoResponseDTO> crear(@Valid @RequestBody CursoRequestDTO request) {
        CursoResponseDTO creado = cursoService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }
}