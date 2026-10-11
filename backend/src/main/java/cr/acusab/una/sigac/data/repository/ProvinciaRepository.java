package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Provincia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProvinciaRepository extends JpaRepository<Provincia, Integer> {
    List<Provincia> findAllByOrderByIdAsc();
}
