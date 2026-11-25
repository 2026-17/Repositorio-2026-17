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
     *
     * El curso es COALESCE(md.id_curso, g.id_curso): en un espacio de
     * instrumento el nivel está en el detalle. Los cursos de instrumento
     * NO se incluyen: se aprueban por instrumento (aprobar Instrumento V en
     * piano no lo aprueba en guitarra) y se consultan con
     * findNivelesInstrumentoAprobados. Lo reconocido fuera de SIGAC está en
     * cursos_reconocidos (AvanceInstrumentoService junta ambas fuentes).
     * Las calificaciones de agrupaciones tampoco: se cuentan por agrupación
     * (regla D5, AvanceAgrupacionService.cursosAprobadosDelPlan).
     */
    @Query(value = """
            SELECT COALESCE(md.id_curso, g.id_curso) AS id_curso, MAX(c.nota_final)
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
                     INNER JOIN cursos cu
                                ON cu.id_curso = COALESCE(md.id_curso, g.id_curso)
            WHERE a.id_estudiante = :idEstudiante
              AND ea.codigo = 'APROBADO'
              AND cu.es_curso_instrumento = FALSE
              AND g.tipo_grupo <> 'AGRUPACION'
            GROUP BY COALESCE(md.id_curso, g.id_curso)
            """, nativeQuery = true)
    List<Object[]> findCursosAprobados(
            @Param("idEstudiante") Long idEstudiante
    );

    /*
     * Niveles de instrumento aprobados en SIGAC, por instrumento: el
     * detalle es de un espacio de instrumento y el nivel es md.id_curso.
     * El instrumento es el del detalle (md.id_instrumento); si no lo tiene,
     * el del espacio. En un espacio de "cualquier instrumento del profesor"
     * el grupo no tiene instrumento.
     *
     * Devuelve filas [idCurso, idInstrumento, notaMaxima, idPeriodo].
     */
    @Query(value = """
            SELECT md.id_curso, COALESCE(md.id_instrumento, g.id_instrumento), MAX(c.nota_final), MAX(g.id_periodo)
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
              AND g.tipo_grupo = 'ESPACIO_INSTRUMENTO'
              AND md.id_curso IS NOT NULL
            GROUP BY md.id_curso, COALESCE(md.id_instrumento, g.id_instrumento)
            """, nativeQuery = true)
    List<Object[]> findNivelesInstrumentoAprobados(
            @Param("idEstudiante") Long idEstudiante
    );

    /*
     * Agrupaciones - Todas las calificaciones del estudiante en grupos de
     * agrupación (aprobadas y no aprobadas). El nivel es md.id_curso; la
     * familia (Conjunto Instrumental o Coro) la decide el servicio por el
     * código del curso.
     *
     * Devuelve filas [idCurso, idAgrupacion, codigoEstado, nota, idPeriodo].
     */
    @Query(value = """
            SELECT md.id_curso, g.id_agrupacion, ea.codigo, c.nota_final, g.id_periodo
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
              AND g.tipo_grupo = 'AGRUPACION'
              AND md.id_curso IS NOT NULL
            """, nativeQuery = true)
    List<Object[]> findCalificacionesAgrupacion(
            @Param("idEstudiante") Long idEstudiante
    );

    /*
     * Espacios de instrumento - Quién ocupa cada espacio: detalles no
     * retirados de matrículas con el estado indicado (REGISTRADA).
     *
     * Devuelve filas [idGrupo, idCurso (nivel), idEstudiante,
     * idInstrumento (el que cursa en el espacio; puede ser nulo en
     * matrículas anteriores al cambio 2026-11-24)].
     */
    @Query("""
            SELECT d.idGrupo, d.idCurso, d.matricula.autorizacion.idEstudiante.id, d.idInstrumento
            FROM MatriculaDetalle d
            WHERE d.idGrupo IN :idsGrupo
              AND d.retirado = false
              AND d.matricula.estado.codigo = :codigoEstado
            """)
    List<Object[]> findOcupantesDeGrupos(
            @Param("idsGrupo") Collection<Long> idsGrupo,
            @Param("codigoEstado") String codigoEstado
    );

    /*
     * Cursos a coordinar en Dirección - Detalles no retirados de matrículas
     * con el estado indicado (REGISTRADA), con el estudiante ya cargado.
     */
    @Query("""
            SELECT d FROM MatriculaDetalle d
              JOIN FETCH d.matricula m
              JOIN FETCH m.autorizacion a
              JOIN FETCH a.idEstudiante
            WHERE d.idGrupo IN :idsGrupo
              AND d.retirado = false
              AND m.estado.codigo = :codigoEstado
            """)
    List<MatriculaDetalle> findDetallesActivosDeGrupos(
            @Param("idsGrupo") Collection<Long> idsGrupo,
            @Param("codigoEstado") String codigoEstado
    );

    /*
     * Gestión de grupos - Cantidad de detalles de matrícula de cada grupo
     * (incluye retirados y matrículas de cualquier estado). Un grupo con
     * historial de matrícula no se puede eliminar ni cambiar de curso o
     * período.
     *
     * Devuelve filas [idGrupo, cantidad].
     */
    @Query("""
            SELECT d.idGrupo, COUNT(d)
            FROM MatriculaDetalle d
            WHERE d.idGrupo IN :idsGrupo
            GROUP BY d.idGrupo
            """)
    List<Object[]> contarDetallesPorGrupo(
            @Param("idsGrupo") Collection<Long> idsGrupo
    );
}
