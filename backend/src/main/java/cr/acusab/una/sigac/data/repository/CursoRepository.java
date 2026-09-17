package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Curso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CursoRepository extends JpaRepository<Curso, Long> {
    boolean existsByCodigo(String codigo);

    // HU-031 - Listado de cursos activos para la pantalla de desactivación.
    List<Curso> findByActivoTrueOrderByNombreAsc();
}