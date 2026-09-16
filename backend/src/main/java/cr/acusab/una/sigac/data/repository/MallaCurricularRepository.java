package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.MallaCurricular;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MallaCurricularRepository
        extends JpaRepository<MallaCurricular, Long> {

    @Query("""
            SELECT MAX(m.ordenEnNivel)
            FROM MallaCurricular m
            WHERE m.idPlanEstudio = :idPlanEstudio
              AND m.nivel = :nivel
            """)
    Optional<Short> findMaxOrdenEnNivel(
            @Param("idPlanEstudio") Long idPlanEstudio,
            @Param("nivel") Short nivel
    );

    List<MallaCurricular> findByIdCursoAndActivoTrue(Long idCurso);

    @Query("""
            SELECT m
            FROM MallaCurricular m
            JOIN FETCH m.idCurso
            WHERE m.idPlanEstudio = :idPlanEstudio
              AND m.activo = true
              AND m.idCurso.activo = true
            ORDER BY m.nivel, m.ordenEnNivel, m.idCurso.nombre
            """)
    List<MallaCurricular> findCursosActivosDePlan(
            @Param("idPlanEstudio") Long idPlanEstudio
    );
}
