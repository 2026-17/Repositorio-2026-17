package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Instrumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InstrumentoRepository extends JpaRepository<Instrumento, Integer> {
    Optional<Instrumento> findByIdAndActivoTrue(Integer id);
}
