package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.GrupoService;
import cr.acusab.una.sigac.presentation.dto.request.GrupoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.GrupoGestionResponse;
import cr.acusab.una.sigac.presentation.dto.response.GrupoResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/*
 * Grupos de un curso en un período. Todos los endpoints son del
 * DIRECTOR_ACADEMICO (ver SecurityConfig).
 */
@RestController
@RequestMapping("/grupos")
public class GrupoController {

    private final GrupoService grupoService;

    public GrupoController(GrupoService grupoService) {
        this.grupoService = grupoService;
    }

    /*
     * Creación de un grupo de un curso en un período, con su horario.
     */
    @PostMapping
    public ResponseEntity<GrupoResponseDTO> crear(
            @Valid @RequestBody GrupoRequestDTO request) {

        GrupoResponseDTO creado = grupoService.crear(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(creado);
    }

    // Listado para la pantalla Grupos; filtros opcionales.
    @GetMapping
    public List<GrupoGestionResponse> listar(
            @RequestParam(required = false) Long idPeriodo,
            @RequestParam(required = false) Long idCurso) {

        return grupoService.listar(idPeriodo, idCurso);
    }

    // Código sugerido: código del curso + primera letra libre en el período.
    @GetMapping("/sugerencia-codigo")
    public Map<String, String> sugerirCodigo(
            @RequestParam Long idCurso,
            @RequestParam(required = false) Long idPeriodo) {

        return Map.of("codigo", grupoService.sugerirCodigo(idCurso, idPeriodo));
    }

    @GetMapping("/{idGrupo}")
    public GrupoGestionResponse obtener(@PathVariable Long idGrupo) {
        return grupoService.obtener(idGrupo);
    }

    @PutMapping("/{idGrupo}")
    public GrupoGestionResponse actualizar(
            @PathVariable Long idGrupo,
            @Valid @RequestBody GrupoRequestDTO request) {

        return grupoService.actualizar(idGrupo, request);
    }

    @DeleteMapping("/{idGrupo}")
    public ResponseEntity<Void> eliminar(@PathVariable Long idGrupo) {
        grupoService.eliminar(idGrupo);
        return ResponseEntity.noContent().build();
    }
}
