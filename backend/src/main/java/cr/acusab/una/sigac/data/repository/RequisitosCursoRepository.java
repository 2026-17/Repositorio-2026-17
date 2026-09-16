package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.RequisitosCurso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RequisitosCursoRepository extends JpaRepository<RequisitosCurso, Long> {

    List<RequisitosCurso> findByIdPlanEstudio_IdAndIdCurso_IdAndActivoTrue(
            Long idPlanEstudio,
            Long idCurso
    );

    @Query("""
            SELECT r
            FROM RequisitosCurso r
            JOIN FETCH r.idCursoRequerido
            JOIN FETCH r.idTipoRequisito
            WHERE r.idPlanEstudio.id = :idPlanEstudio
              AND r.idCurso.id = :idCurso
              AND r.activo = true
            ORDER BY r.idTipoRequisito.codigo, r.idCursoRequerido.nombre
            """)
    List<RequisitosCurso> findConfiguracionActiva(
            @Param("idPlanEstudio") Long idPlanEstudio,
            @Param("idCurso") Long idCurso
    );

    @Query("""
            SELECT r
            FROM RequisitosCurso r
            JOIN FETCH r.idCurso
            JOIN FETCH r.idCursoRequerido
            WHERE r.idPlanEstudio.id = :idPlanEstudio
              AND r.idTipoRequisito.codigo = :codigoTipo
              AND r.activo = true
            """)
    List<RequisitosCurso> findActivosPorPlanYTipo(
            @Param("idPlanEstudio") Long idPlanEstudio,
            @Param("codigoTipo") String codigoTipo
    );
}
