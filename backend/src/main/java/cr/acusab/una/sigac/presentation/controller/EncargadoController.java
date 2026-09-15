package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.EncargadoService;
import cr.acusab.una.sigac.presentation.dto.request.EncargadoCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.EncargadoUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.response.EncargadoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/encargados")
public class EncargadoController {

    private final EncargadoService encargadoService;

    public EncargadoController(EncargadoService encargadoService) {
        this.encargadoService = encargadoService;
    }

    @PostMapping
    public ResponseEntity<EncargadoResponse> registrar(
            @Valid @RequestBody EncargadoCreateRequest request
    ) {
        EncargadoResponse response =
                encargadoService.registrar(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/estudiante/{idEstudiante}")
    public ResponseEntity<List<EncargadoResponse>> listarPorEstudiante(
            @PathVariable Long idEstudiante
    ) {
        return ResponseEntity.ok(
                encargadoService.listarPorEstudiante(idEstudiante)
        );
    }

    @GetMapping("/estudiante/{idEstudiante}/{idEncargado}")
    public ResponseEntity<EncargadoResponse> obtener(
            @PathVariable Long idEstudiante,
            @PathVariable Long idEncargado
    ) {
        return ResponseEntity.ok(
                encargadoService.obtener(
                        idEstudiante,
                        idEncargado
                )
        );
    }

    @PutMapping("/estudiante/{idEstudiante}/{idEncargado}")
    public ResponseEntity<EncargadoResponse> modificar(
            @PathVariable Long idEstudiante,
            @PathVariable Long idEncargado,
            @Valid @RequestBody EncargadoUpdateRequest request
    ) {
        return ResponseEntity.ok(
                encargadoService.modificar(
                        idEstudiante,
                        idEncargado,
                        request
                )
        );
    }
}