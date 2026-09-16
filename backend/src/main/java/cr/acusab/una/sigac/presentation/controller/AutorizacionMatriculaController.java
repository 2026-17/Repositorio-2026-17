package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.AutorizacionMatriculaService;
import cr.acusab.una.sigac.presentation.dto.request.ConfirmarMatriculaRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.AutorizacionMatriculaResponseDTO;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/autorizaciones-matricula")
public class AutorizacionMatriculaController {

    private final AutorizacionMatriculaService autorizacionMatriculaService;

    public AutorizacionMatriculaController(
            AutorizacionMatriculaService autorizacionMatriculaService
    ) {
        this.autorizacionMatriculaService =
                autorizacionMatriculaService;
    }

    /*
     * HU-012 - Consultar solicitudes pendientes.
     */
    @GetMapping("/pendientes")
    public ResponseEntity<List<AutorizacionMatriculaResponseDTO>>
    listarPendientes() {

        return ResponseEntity.ok(
                autorizacionMatriculaService
                        .listarPendientes()
        );
    }

    /*
     * HU-012 - Consultar historial de solicitudes
     * ya aprobadas o rechazadas.
     */
    @GetMapping("/historial")
    public ResponseEntity<List<AutorizacionMatriculaResponseDTO>>
    listarHistorial() {

        return ResponseEntity.ok(
                autorizacionMatriculaService
                        .listarHistorial()
        );
    }

    /*
     * HU-012 - Aprobar o rechazar una solicitud.
     */
    @PatchMapping("/{idAutorizacion}/confirmar")
    public ResponseEntity<AutorizacionMatriculaResponseDTO>
    confirmar(
            @PathVariable Long idAutorizacion,
            @Valid @RequestBody ConfirmarMatriculaRequestDTO request
    ) {

        return ResponseEntity.ok(
                autorizacionMatriculaService.confirmar(
                        idAutorizacion,
                        request
                )
        );
    }
}