package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Agrupacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgrupacionRepository extends JpaRepository<Agrupacion, Long> {

    // Catálogo completo (activas e inactivas), en orden alfabético.
    List<Agrupacion> findAllByOrderByNombreAsc();

    // El código y el nombre no se repiten (uq_agrupaciones_codigo y uq_agrupaciones_nombre).
    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
