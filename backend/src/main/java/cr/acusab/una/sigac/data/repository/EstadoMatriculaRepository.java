package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.EstadoMatricula;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstadoMatriculaRepository
        extends JpaRepository<EstadoMatricula, Integer> {

    Optional<EstadoMatricula> findByCodigoAndActivoTrue(String codigo);
}
