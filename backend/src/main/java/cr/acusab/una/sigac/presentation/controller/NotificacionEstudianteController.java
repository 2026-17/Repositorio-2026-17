package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.NotificacionEstudianteService;
import cr.acusab.una.sigac.presentation.dto.response.NotificacionEstudianteResponseDTO;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notificaciones-estudiante")
public class NotificacionEstudianteController {

    private final NotificacionEstudianteService notificacionEstudianteService;

    public NotificacionEstudianteController(
            NotificacionEstudianteService notificacionEstudianteService
    ) {
        this.notificacionEstudianteService =
                notificacionEstudianteService;
    }

    /*
     * HU-012 - Consultar las notificaciones
     * pertenecientes al estudiante autenticado.
     */
    @GetMapping("/mias")
    public ResponseEntity<List<NotificacionEstudianteResponseDTO>>
    listarMisNotificaciones() {

        return ResponseEntity.ok(
                notificacionEstudianteService
                        .listarMisNotificaciones()
        );
    }

    /*
     * HU-012 - Marcar una notificación como leída.
     */
    @PatchMapping("/{idNotificacion}/leer")
    public ResponseEntity<NotificacionEstudianteResponseDTO>
    marcarComoLeida(
            @PathVariable Long idNotificacion
    ) {

        return ResponseEntity.ok(
                notificacionEstudianteService
                        .marcarComoLeida(
                                idNotificacion
                        )
        );
    }
}