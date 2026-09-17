package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.NotificacionEstudiante;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.NotificacionEstudianteRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.response.NotificacionEstudianteResponseDTO;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificacionEstudianteService {

    private final NotificacionEstudianteRepository notificacionRepository;
    private final EstudianteRepository estudianteRepository;

    public NotificacionEstudianteService(
            NotificacionEstudianteRepository notificacionRepository,
            EstudianteRepository estudianteRepository
    ) {
        this.notificacionRepository = notificacionRepository;
        this.estudianteRepository = estudianteRepository;
    }

    /*
     * ============================================================
     * HU-012 - Consultar mis notificaciones
     * ============================================================
     *
     * El estudiante autenticado únicamente puede consultar
     * notificaciones asociadas a su propio registro.
     */
    @Transactional(readOnly = true)
    public List<NotificacionEstudianteResponseDTO> listarMisNotificaciones() {

        Estudiante estudiante =
                obtenerEstudianteAutenticado();

        return notificacionRepository
                .findByIdEstudiante_IdOrderByFechaNotificacionDesc(
                        estudiante.getId()
                )
                .stream()
                .map(this::mapearAResponse)
                .toList();
    }

    /*
     * ============================================================
     * HU-012 - Marcar una notificación como leída
     * ============================================================
     */
    @Transactional
    public NotificacionEstudianteResponseDTO marcarComoLeida(
            Long idNotificacion
    ) {

        Estudiante estudiante =
                obtenerEstudianteAutenticado();

        NotificacionEstudiante notificacion =
                notificacionRepository
                        .findById(idNotificacion)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "La notificación indicada no existe."
                                )
                        );

        /*
         * Evita que un estudiante pueda modificar una
         * notificación perteneciente a otra persona.
         */
        if (notificacion.getIdEstudiante() == null
                || notificacion.getIdEstudiante().getId() == null
                || !notificacion.getIdEstudiante()
                .getId()
                .equals(estudiante.getId())) {

            throw new BusinessException(
                    "La notificación indicada no pertenece al estudiante autenticado."
            );
        }

        /*
         * Si ya estaba leída, simplemente se devuelve sin
         * modificar nuevamente la fecha.
         */
        if (!Boolean.TRUE.equals(
                notificacion.getLeida()
        )) {

            notificacion.setLeida(
                    true
            );

            notificacion.setFechaLectura(
                    Instant.now()
            );

            notificacion =
                    notificacionRepository.save(
                            notificacion
                    );
        }

        return mapearAResponse(
                notificacion
        );
    }

    /*
     * ============================================================
     * Identificar estudiante autenticado
     * ============================================================
     *
     * El JWT guarda la cédula como authentication.getName().
     */
    private Estudiante obtenerEstudianteAutenticado() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {

            throw new BusinessException(
                    "No se pudo identificar al usuario autenticado."
            );
        }

        String cedula =
                authentication.getName();

        return estudianteRepository
                .findByUsuario_Cedula(
                        cedula
                )
                .orElseThrow(() ->
                        new BusinessException(
                                "El usuario autenticado no está asociado a un estudiante."
                        )
                );
    }

    /*
     * ============================================================
     * Conversión a DTO
     * ============================================================
     */
    private NotificacionEstudianteResponseDTO mapearAResponse(
            NotificacionEstudiante notificacion
    ) {

        NotificacionEstudianteResponseDTO response =
                new NotificacionEstudianteResponseDTO();

        response.setIdNotificacion(
                notificacion.getId()
        );

        if (notificacion.getIdAutorizacion() != null) {

            response.setIdAutorizacion(
                    notificacion
                            .getIdAutorizacion()
                            .getId()
            );
        }

        response.setTipo(
                notificacion.getTipo()
        );

        response.setAsunto(
                notificacion.getAsunto()
        );

        response.setMensaje(
                notificacion.getMensaje()
        );

        response.setLeida(
                notificacion.getLeida()
        );

        response.setFechaNotificacion(
                notificacion.getFechaNotificacion()
        );

        response.setFechaLectura(
                notificacion.getFechaLectura()
        );

        return response;
    }
}