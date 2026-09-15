package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Estudiante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {
    boolean existsByIdTipoIdentificacion_IdAndNumeroIdentificacion(
            Integer idTipoIdentificacion, String numeroIdentificacion);

    Optional<Estudiante> findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
            Integer idTipoIdentificacion, String numeroIdentificacion);
}
