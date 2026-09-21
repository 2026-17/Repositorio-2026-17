package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.MatriculaDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MatriculaDetalleRepository
        extends JpaRepository<MatriculaDetalle, Long> {

    /*
     * Historia 2 - Grupos que el estudiante ya tiene matriculados
     * (no retirados) dentro de una matrícula.
     */
    List<MatriculaDetalle> findByMatricula_IdAndRetiradoFalse(
            Long idMatricula
    );

    /*
     * Historia 2 - Cupos ocupados por grupo. Un cupo está ocupado
     * mientras el grupo esté en una matrícula con el estado indicado
     * (REGISTRADA) y no haya sido retirado.
     *
     * Devuelve filas [idGrupo, cantidad].
     */
    @Query("""
            SELECT d.idGrupo, COUNT(d)
            FROM MatriculaDetalle d
            WHERE d.idGrupo IN :idsGrupo
              AND d.retirado = false
              AND d.matricula.estado.codigo = :codigoEstado
            GROUP BY d.idGrupo
            """)
    List<Object[]> contarCuposOcupados(
            @Param("idsGrupo") Collection<Long> idsGrupo,
            @Param("codigoEstado") String codigoEstado
    );

    /*
     * Historia 2 - Cursos que el estudiante ya aprobó y la mejor nota
     * obtenida en cada uno. Solo cuenta el estado APROBADO, igual que la
     * validación de requisitos de la base de datos.
     *
     * Devuelve filas [idCurso, notaMaxima]. La nota puede ser nula en
     * cursos con valoración cualitativa.
     */
    @Query(value = """
            SELECT g.id_curso, MAX(c.nota_final)
            FROM calificaciones c
                     INNER JOIN estados_academicos ea
                                ON ea.id_estado_academico = c.id_estado_academico
                     INNER JOIN matriculas_detalles md
                                ON md.id_detalle_matricula = c.id_detalle_matricula
                     INNER JOIN matriculas m
                                ON m.id_matricula = md.id_matricula
                     INNER JOIN autorizaciones_matricula a
                                ON a.id_autorizacion = m.id_autorizacion
                     INNER JOIN grupos g
                                ON g.id_grupo = md.id_grupo
            WHERE a.id_estudiante = :idEstudiante
              AND ea.codigo = 'APROBADO'
            GROUP BY g.id_curso
            """, nativeQuery = true)
    List<Object[]> findCursosAprobados(
            @Param("idEstudiante") Long idEstudiante
    );
}
