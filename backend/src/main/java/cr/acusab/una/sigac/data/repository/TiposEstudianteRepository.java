package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.TiposEstudiante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TiposEstudianteRepository extends JpaRepository<TiposEstudiante, Integer> {

    Optional<TiposEstudiante> findByIdAndActivoTrue(Integer id);

    Optional<TiposEstudiante> findByCodigo(String codigo);

    List<TiposEstudiante> findByActivoTrueOrderByNombreAsc();
}
