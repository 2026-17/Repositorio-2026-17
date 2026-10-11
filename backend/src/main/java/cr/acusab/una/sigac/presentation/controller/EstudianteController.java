package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.DocumentoEstudianteService;
import cr.acusab.una.sigac.logic.service.EstudianteService;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.response.DocumentoEstudianteResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteBusquedaResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteListadoResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/estudiantes")
@PreAuthorize("hasRole('ADMINISTRATIVO')")
public class EstudianteController {

    private final EstudianteService estudianteService;
    private final DocumentoEstudianteService documentoEstudianteService;

    public EstudianteController(
            EstudianteService estudianteService,
            DocumentoEstudianteService documentoEstudianteService
    ) {
        this.estudianteService = estudianteService;
        this.documentoEstudianteService = documentoEstudianteService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EstudianteResponse> crear(
            @Valid @RequestPart("estudiante") EstudianteCreateRequest request,
            @RequestPart("politica") MultipartFile politica
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(estudianteService.crear(request, politica));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR_ACADEMICO')")
    public ResponseEntity<List<EstudianteBusquedaResponse>> buscar(
            @RequestParam(required = false) String texto
    ) {
        return ResponseEntity.ok(estudianteService.buscar(texto));
    }

    @GetMapping("/listado")
    public ResponseEntity<List<EstudianteListadoResponse>> listado(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Boolean activo
    ) {
        return ResponseEntity.ok(
                estudianteService.listado(texto, activo)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<EstudianteResponse> obtener(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                estudianteService.obtener(id)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<EstudianteResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody EstudianteUpdateRequest request
    ) {
        return ResponseEntity.ok(
                estudianteService.actualizar(id, request)
        );
    }

    /*
     * ============================================================
     * HU-022 - Adjuntar documentos del estudiante
     * ============================================================
     */

    /**
     * Adjunta un documento PDF al expediente del estudiante.
     *
     * Si existe un documento vigente del mismo tipo,
     * el servicio lo reemplaza conservándolo como historial.
     */
    @PostMapping(
            value = "/{id}/documentos",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<DocumentoEstudianteResponse> adjuntarDocumento(
            @PathVariable Long id,

            @RequestParam("idTipoDocumento")
            Integer idTipoDocumento,

            @RequestPart("archivo")
            MultipartFile archivo,

            @RequestParam(
                    value = "fechaEmision",
                    required = false
            )
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fechaEmision,

            @RequestParam(
                    value = "fechaVencimiento",
                    required = false
            )
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fechaVencimiento
    ) {
        DocumentoEstudianteResponse response =
                documentoEstudianteService.adjuntarOReemplazar(
                        id,
                        idTipoDocumento,
                        archivo,
                        fechaEmision,
                        fechaVencimiento
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Devuelve únicamente los documentos vigentes
     * asociados al expediente del estudiante.
     */
    @GetMapping("/{id}/documentos")
    public ResponseEntity<List<DocumentoEstudianteResponse>> listarDocumentos(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(
                documentoEstudianteService.listarActuales(id)
        );
    }
}