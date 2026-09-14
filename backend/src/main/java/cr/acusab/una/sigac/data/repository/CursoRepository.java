package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Curso;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CursoRepository extends JpaRepository<Curso, Long> {
    boolean existsByCodigo(String codigo);
}