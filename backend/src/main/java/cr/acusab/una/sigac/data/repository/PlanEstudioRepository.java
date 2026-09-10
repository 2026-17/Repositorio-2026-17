package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.PlanEstudio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanEstudioRepository extends JpaRepository<PlanEstudio, Long> {
    // Busca el plan vigente de un programa (regla automática que definimos)
    Optional<PlanEstudio> findFirstByIdProgramaAndVigenteTrue(Long idPrograma);
}
