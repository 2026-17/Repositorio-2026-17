package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Profesor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfesorRepository extends JpaRepository<Profesor, Long> {

    // La identificacion es unica por tipo (uq_profesores_identificacion en la BD)
    Optional<Profesor> findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
            Integer idTipoIdentificacion, String numeroIdentificacion);

    List<Profesor> findByActivo(Boolean activo);
}
