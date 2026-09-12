package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.TiposIdentificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoIdentificacionRepository extends JpaRepository<TiposIdentificacion, Integer> {
    Optional<TiposIdentificacion> findByIdAndActivoTrue(Integer id);
}
