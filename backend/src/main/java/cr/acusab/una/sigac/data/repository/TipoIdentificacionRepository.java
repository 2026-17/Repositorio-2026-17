package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.TipoIdentificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoIdentificacionRepository extends JpaRepository<TipoIdentificacion, Integer> {
    Optional<TipoIdentificacion> findByIdAndActivoTrue(Integer id);
}
