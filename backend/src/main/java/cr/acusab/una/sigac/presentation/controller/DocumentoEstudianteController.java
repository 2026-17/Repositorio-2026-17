package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.DocumentoEstudianteService;
import cr.acusab.una.sigac.presentation.dto.response.DocumentoEstudianteResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/estudiantes/{idEstudiante}/documentos")
public class DocumentoEstudianteController {

    private final DocumentoEstudianteService documentoEstudianteService;

    public DocumentoEstudianteController(DocumentoEstudianteService documentoEstudianteService) {
        this.documentoEstudianteService = documentoEstudianteService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<DocumentoEstudianteResponse> adjuntar(
            @PathVariable Long idEstudiante,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam Integer idTipoDocumento,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaEmision,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaVencimiento
    ) {
        DocumentoEstudianteResponse response = documentoEstudianteService.adjuntar(
                idEstudiante, idTipoDocumento, archivo, fechaEmision, fechaVencimiento);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<DocumentoEstudianteResponse>> listar(@PathVariable Long idEstudiante) {
        return ResponseEntity.ok(documentoEstudianteService.listarPorEstudiante(idEstudiante));
    }
}
