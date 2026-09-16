package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.RequisitoCurso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RequisitoCursoRepository extends JpaRepository<RequisitoCurso, Long> {

    List<RequisitoCurso> findByIdPlanEstudioAndIdCursoAndActivoTrue(Long idPlanEstudio, Long idCurso);

    List<RequisitoCurso> findByIdPlanEstudioAndActivoTrue(Long idPlanEstudio);

    Optional<RequisitoCurso> findByIdPlanEstudioAndIdCursoAndIdCursoRequeridoAndIdTipoRequisito(
            Long idPlanEstudio,
            Long idCurso,
            Long idCursoRequerido,
            Short idTipoRequisito
    );
}
