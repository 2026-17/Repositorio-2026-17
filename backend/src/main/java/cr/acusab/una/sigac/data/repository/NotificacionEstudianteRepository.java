package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.NotificacionEstudiante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacionEstudianteRepository
        extends JpaRepository<NotificacionEstudiante, Long> {

    List<NotificacionEstudiante>
    findByIdEstudiante_IdOrderByFechaNotificacionDesc(
            Long idEstudiante
    );
}