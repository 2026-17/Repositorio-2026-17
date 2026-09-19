package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.TiposRequisitosCurso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TiposRequisitosCursoRepository extends JpaRepository<TiposRequisitosCurso, Integer> {
    Optional<TiposRequisitosCurso> findByCodigo(String codigo);
}
