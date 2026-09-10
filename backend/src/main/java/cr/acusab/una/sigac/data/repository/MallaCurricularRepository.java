package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.MallaCurricular;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MallaCurricularRepository extends JpaRepository<MallaCurricular, Long> {
    // Para calcular el siguiente "orden_en_nivel" automáticamente
    Integer countByIdPlanEstudioAndNivel(Long idPlanEstudio, Short nivel);
}