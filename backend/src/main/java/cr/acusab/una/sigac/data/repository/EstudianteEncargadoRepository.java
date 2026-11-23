package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.EstudiantesEncargado;
import cr.acusab.una.sigac.data.entity.EstudiantesEncargadoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EstudianteEncargadoRepository
        extends JpaRepository<EstudiantesEncargado, EstudiantesEncargadoId> {

    @Query("""
            SELECT relacion
            FROM EstudiantesEncargado relacion
            WHERE relacion.id.idEstudiante = :idEstudiante
            """)
    List<EstudiantesEncargado> findByIdEstudiante(
            @Param("idEstudiante") Long idEstudiante
    );

    @Query("""
            SELECT relacion
            FROM EstudiantesEncargado relacion
            WHERE relacion.id.idEstudiante = :idEstudiante
              AND relacion.id.idEncargado = :idEncargado
            """)
    Optional<EstudiantesEncargado> findByIdEstudianteAndIdEncargado(
            @Param("idEstudiante") Long idEstudiante,
            @Param("idEncargado") Long idEncargado
    );

    @Query("""
            SELECT CASE WHEN COUNT(relacion) > 0 THEN true ELSE false END
            FROM EstudiantesEncargado relacion
            WHERE relacion.id.idEstudiante = :idEstudiante
              AND relacion.id.idEncargado = :idEncargado
            """)
    boolean existsByIdEstudianteAndIdEncargado(
            @Param("idEstudiante") Long idEstudiante,
            @Param("idEncargado") Long idEncargado
    );

    // Relaciones del encargado con estudiantes distintos al indicado.
    @Query("""
            SELECT relacion
            FROM EstudiantesEncargado relacion
            JOIN FETCH relacion.idEstudiante
            WHERE relacion.id.idEncargado = :idEncargado
              AND relacion.id.idEstudiante <> :idEstudiante
            """)
    List<EstudiantesEncargado> findDeOtrosEstudiantes(
            @Param("idEncargado") Long idEncargado,
            @Param("idEstudiante") Long idEstudiante
    );

    @Query("""
            SELECT COUNT(relacion)
            FROM EstudiantesEncargado relacion
            WHERE relacion.id.idEstudiante = :idEstudiante
            """)
    long countByIdEstudiante(
            @Param("idEstudiante") Long idEstudiante
    );
}