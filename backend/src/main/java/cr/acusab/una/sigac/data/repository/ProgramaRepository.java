package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Programa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProgramaRepository extends JpaRepository<Programa, Long> {

    List<Programa> findByActivoTrueOrderByNombreAsc();

    // Gestión de programas: listado completo (activos e inactivos).
    List<Programa> findAllByOrderByNombreAsc();

    // La tabla exige código y nombre únicos (uq_programas_codigo / uq_programas_nombre).
    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
