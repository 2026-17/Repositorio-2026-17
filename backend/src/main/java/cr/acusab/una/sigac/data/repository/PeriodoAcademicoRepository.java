package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PeriodoAcademicoRepository
        extends JpaRepository<PeriodoAcademico, Long> {

    List<PeriodoAcademico> findByEstadoInOrderByFechaInicioDesc(
            Collection<String> estados
    );

    boolean existsByCodigo(String codigo);

    boolean existsByNombre(String nombre);

    boolean existsByNombreAndIdNot(String nombre, Long id);
}
