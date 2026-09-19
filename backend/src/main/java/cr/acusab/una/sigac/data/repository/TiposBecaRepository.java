package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.TiposBeca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TiposBecaRepository extends JpaRepository<TiposBeca, Integer> {
    Optional<TiposBeca> findByIdAndActivoTrue(Integer id);

    List<TiposBeca> findByActivoTrueOrderByNombreAsc();
}
