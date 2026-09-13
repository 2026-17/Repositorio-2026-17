package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.PlanEstudio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanEstudioRepository
        extends JpaRepository<PlanEstudio, Long> {

    Optional<PlanEstudio>
    findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc(
            Long idPrograma
    );
}
