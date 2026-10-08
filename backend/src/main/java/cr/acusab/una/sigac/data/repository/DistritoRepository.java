package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Distrito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DistritoRepository extends JpaRepository<Distrito, Integer> {
    List<Distrito> findByCanton_CodigoOrderByCodigoAsc(Integer codigoCanton);
}
