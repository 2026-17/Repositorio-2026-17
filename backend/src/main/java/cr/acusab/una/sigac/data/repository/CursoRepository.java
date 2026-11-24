package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Curso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CursoRepository extends JpaRepository<Curso, Long> {
    boolean existsByCodigo(String codigo);

    // HU-031 - Listado de cursos activos para la pantalla de desactivación.
    List<Curso> findByActivoTrueOrderByNombreAsc();

    // Agrupaciones: niveles de una familia (CI-I..CI-XII, CORO-1..CORO-XIV) por código.
    List<Curso> findByCodigoIn(Collection<String> codigos);
}
