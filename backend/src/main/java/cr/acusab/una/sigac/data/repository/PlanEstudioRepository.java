package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.PlanEstudio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanEstudioRepository
        extends JpaRepository<PlanEstudio, Long> {

    Optional<PlanEstudio>
    findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc(
            Long idPrograma
    );

    List<PlanEstudio> findByVigenteTrueOrderByNombreAsc();

    // Gestión de programas: todas las versiones del plan de un programa.
    List<PlanEstudio> findByIdProgramaOrderByFechaVigenciaDesdeDesc(Long idPrograma);

    // La tabla exige código único y versión única por programa.
    boolean existsByCodigo(String codigo);

    boolean existsByIdProgramaAndVersionIgnoreCase(Long idPrograma, String version);
}
