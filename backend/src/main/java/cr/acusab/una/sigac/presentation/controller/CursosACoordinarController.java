package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.CursosACoordinarService;
import cr.acusab.una.sigac.presentation.dto.request.CoordinacionDetalleRequest;
import cr.acusab.una.sigac.presentation.dto.response.CursoACoordinarResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/*
 * Cursos a coordinar en Dirección (Música de Cámara): la Dirección
 * Académica ve quién los matriculó y, estudiante por estudiante, asigna
 * (o no) un profesor y anota cómo lo cumplirá.
 */
@RestController
@RequestMapping("/cursos-a-coordinar")
@PreAuthorize("hasRole('DIRECTOR_ACADEMICO')")
public class CursosACoordinarController {

    private final CursosACoordinarService cursosACoordinarService;

    public CursosACoordinarController(CursosACoordinarService cursosACoordinarService) {
        this.cursosACoordinarService = cursosACoordinarService;
    }

    // Grupos del período (por defecto, el de matrícula o en curso) con sus estudiantes.
    @GetMapping
    public ResponseEntity<List<CursoACoordinarResponse>> listar(@RequestParam(required = false) Long idPeriodo) {
        return ResponseEntity.ok(cursosACoordinarService.listar(idPeriodo));
    }

    // Profesor asignado y notas de un estudiante (idProfesorAsignado null = sin asignar).
    @PatchMapping("/detalles/{idDetalle}")
    public ResponseEntity<CursoACoordinarResponse.Estudiante> actualizarDetalle(
            @PathVariable Long idDetalle,
            @Valid @RequestBody CoordinacionDetalleRequest request) {
        return ResponseEntity.ok(cursosACoordinarService.actualizarDetalle(idDetalle, request));
    }
}
