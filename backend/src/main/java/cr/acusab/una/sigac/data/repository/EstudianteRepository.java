package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Estudiante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {

    // La identificacion es unica por tipo (uq_estudiantes_identificacion en la BD)
    Optional<Estudiante> findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
            Integer idTipoIdentificacion, String numeroIdentificacion);

    List<Estudiante> findByActivo(Boolean activo);
}
