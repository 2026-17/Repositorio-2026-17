package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.ProgramaService;
import cr.acusab.una.sigac.presentation.dto.request.PlanEstudioInicialRequest;
import cr.acusab.una.sigac.presentation.dto.request.ProgramaCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.ProgramaEstadoRequest;
import cr.acusab.una.sigac.presentation.dto.request.ProgramaUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.response.ProgramaDetalleResponse;
import cr.acusab.una.sigac.presentation.dto.response.ProgramaDisponibilidadResponse;
import cr.acusab.una.sigac.presentation.dto.response.ProgramaResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/*
 * Programas académicos. Los permisos (gestión solo para
 * DIRECTOR_ACADEMICO) se definen en SecurityConfig.
 */
@RestController
@RequestMapping("/programas")
public class ProgramaController {

    private final ProgramaService programaService;

    public ProgramaController(ProgramaService programaService) {
        this.programaService = programaService;
    }

    // Programas activos: selector de Crear curso.
    @GetMapping
    public List<ProgramaResponse> listarProgramasActivos() {
        return programaService.listarProgramasActivos();
    }

    // Listado completo con resumen: pantalla Programas académicos.
    @GetMapping("/gestion")
    public List<ProgramaDetalleResponse> listarParaGestion() {
        return programaService.listarParaGestion();
    }

    // Validación en vivo del código y el nombre en el asistente.
    @GetMapping("/disponibilidad")
    public ProgramaDisponibilidadResponse consultarDisponibilidad(
            @RequestParam(required = false) String codigo,
            @RequestParam(required = false) String nombre) {

        return programaService.consultarDisponibilidad(codigo, nombre);
    }

    @GetMapping("/{idPrograma}")
    public ProgramaDetalleResponse obtener(@PathVariable Long idPrograma) {
        return programaService.obtener(idPrograma);
    }

    @PostMapping
    public ResponseEntity<ProgramaDetalleResponse> crear(
            @Valid @RequestBody ProgramaCreateRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(programaService.crear(request));
    }

    // Primer plan de estudio de un programa que quedó sin plan vigente.
    @PostMapping("/{idPrograma}/planes")
    public ResponseEntity<ProgramaDetalleResponse> agregarPlanInicial(
            @PathVariable Long idPrograma,
            @Valid @RequestBody PlanEstudioInicialRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(programaService.agregarPlanInicial(idPrograma, request));
    }

    @PutMapping("/{idPrograma}")
    public ProgramaDetalleResponse actualizar(
            @PathVariable Long idPrograma,
            @Valid @RequestBody ProgramaUpdateRequest request) {

        return programaService.actualizar(idPrograma, request);
    }

    @PatchMapping("/{idPrograma}/estado")
    public ProgramaDetalleResponse cambiarEstado(
            @PathVariable Long idPrograma,
            @Valid @RequestBody ProgramaEstadoRequest request) {

        return programaService.cambiarEstado(idPrograma, request.getActivo());
    }
}
