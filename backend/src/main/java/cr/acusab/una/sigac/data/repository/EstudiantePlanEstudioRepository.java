package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.EstudiantePlanEstudio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstudiantePlanEstudioRepository
        extends JpaRepository<EstudiantePlanEstudio, Long> {

    /*
     * Historia 2 - Planes en los que el estudiante está inscrito.
     */
    List<EstudiantePlanEstudio> findByIdEstudianteAndActivoTrue(
            Long idEstudiante
    );

    /*
     * Todas las inscripciones del estudiante, activas o no. Se usa al
     * editar sus planes para reactivar en lugar de duplicar.
     */
    List<EstudiantePlanEstudio> findByIdEstudiante(
            Long idEstudiante
    );
}
