package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Programa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProgramaRepository extends JpaRepository<Programa, Long> {

    List<Programa> findByActivoTrueOrderByNombreAsc();
}
