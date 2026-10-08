package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Canton;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CantonRepository extends JpaRepository<Canton, Integer> {
    List<Canton> findByProvincia_IdOrderByCodigoAsc(Integer idProvincia);
}
