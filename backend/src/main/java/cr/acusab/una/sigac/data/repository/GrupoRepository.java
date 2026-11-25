package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Grupo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface GrupoRepository extends JpaRepository<Grupo, Long> {

    /*
     * HU-031 - Listado de cursos activos.
     *
     * Un curso puede tener varios grupos (distintos periodos).
     * Se utiliza el mas reciente como representativo para mostrar
     * docente y cupos en el listado.
     */
    Optional<Grupo> findFirstByIdCursoOrderByCreadoEnDesc(Long idCurso);

    List<Grupo> findByIdCursoInOrderByCreadoEnDesc(List<Long> idCursos);

    /*
     * Crear grupo - El código no se puede repetir dentro del mismo
     * período (uq_grupos_periodo_codigo en la BD).
     */
    boolean existsByIdPeriodoAndCodigo(Long idPeriodo, String codigo);

    // Modificar grupo: el código no puede repetirse con otro grupo del período.
    boolean existsByIdPeriodoAndCodigoAndIdNot(Long idPeriodo, String codigo, Long id);

    // Eliminar profesor: no se permite si tiene espacios de instrumento (quedarían sin profesor).
    boolean existsByIdProfesorAndTipoGrupo(Long idProfesor, String tipoGrupo);

    // Gestión de grupos: listado completo, del período más reciente al más antiguo.
    List<Grupo> findAllByOrderByIdPeriodoDescCodigoAsc();

    /*
     * Gestión de grupos - Sesiones de clase y avances curriculares
     * registrados por grupo. Las tablas no tienen entidad JPA; un grupo
     * con clases registradas no se puede eliminar (la BD las borraría
     * en cascada junto con la asistencia).
     *
     * Devuelven filas [idGrupo, cantidad].
     */
    @Query(value = """
            SELECT id_grupo, COUNT(*)
            FROM sesiones_grupos
            WHERE id_grupo IN (:idsGrupo)
            GROUP BY id_grupo
            """, nativeQuery = true)
    List<Object[]> contarSesionesPorGrupo(@Param("idsGrupo") Collection<Long> idsGrupo);

    @Query(value = """
            SELECT id_grupo, COUNT(*)
            FROM avances_curriculares
            WHERE id_grupo IN (:idsGrupo)
            GROUP BY id_grupo
            """, nativeQuery = true)
    List<Object[]> contarAvancesPorGrupo(@Param("idsGrupo") Collection<Long> idsGrupo);

    /*
     * Historia 2 - Oferta de grupos de un período, filtrada por estado.
     */
    List<Grupo> findByIdPeriodoAndEstadoIn(
            Long idPeriodo,
            Collection<String> estados
    );

    List<Grupo> findByEstadoInOrderByIdPeriodoAscCodigoAsc(
            Collection<String> estados
    );

    /*
     * HU-014: incluye grupos del profesor en cualquier periodo cuyas fechas
     * se traslapen con el periodo del grupo destino. No limita la validacion
     * al mismo id de periodo, porque dos periodos pueden coincidir en fechas.
     */
    /*
     * Agrupaciones: también cuentan los grupos donde el profesor está en
     * grupos_profesores sin ser el responsable.
     */
    @Query("""
            SELECT g FROM Grupo g, PeriodoAcademico periodoGrupo, PeriodoAcademico periodoDestino
            WHERE (g.idProfesor = :idProfesor
                   OR EXISTS (SELECT 1 FROM GrupoProfesor gp
                              WHERE gp.idGrupo = g.id AND gp.idProfesor = :idProfesor))
              AND g.idPeriodo = periodoGrupo.id
              AND periodoDestino.id = :idPeriodo
              AND COALESCE(g.fechaInicio, periodoGrupo.fechaInicio) <= :fechaFinDestino
              AND COALESCE(g.fechaFin, periodoGrupo.fechaFin) >= :fechaInicioDestino
              AND g.estado NOT IN ('CANCELADO', 'CERRADO')
            """)
    List<Grupo> findGruposProfesorEnPeriodo(
            @Param("idProfesor") Long idProfesor,
            @Param("idPeriodo") Long idPeriodo,
            @Param("fechaInicioDestino") LocalDate fechaInicioDestino,
            @Param("fechaFinDestino") LocalDate fechaFinDestino
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Grupo g WHERE g.id = :id")
    Optional<Grupo> findByIdParaAsignacion(
            @Param("id") Long id
    );

    /*
     * Historia 2 - Bloquea los grupos mientras se verifican y descuentan
     * los cupos, para que dos confirmaciones simultáneas no ocupen el
     * mismo último cupo. Se ordena por id para evitar interbloqueos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Grupo g WHERE g.id IN :ids ORDER BY g.id")
    List<Grupo> findAllByIdParaActualizar(
            @Param("ids") Collection<Long> ids
    );

    /*
     * Espacios de instrumento - Agenda: espacios de un período, filtrados
     * opcionalmente por instrumento y profesor (un filtro nulo no filtra).
     * Usa idx_grupos_espacios (id_periodo, id_profesor, id_instrumento).
     *
     * Al filtrar por instrumento también vienen los espacios de "cualquier
     * instrumento del profesor" (id_instrumento NULL). Quien llama debe
     * quedarse solo con los de profesores que dan ese instrumento
     * (EspecialidadProfesor.da), porque eso depende de su especialidad.
     */
    @Query("""
            SELECT g FROM Grupo g
            WHERE g.tipoGrupo = 'ESPACIO_INSTRUMENTO'
              AND (:idPeriodo IS NULL OR g.idPeriodo = :idPeriodo)
              AND (:idProfesor IS NULL OR g.idProfesor = :idProfesor)
              AND (:idInstrumento IS NULL OR g.idInstrumento = :idInstrumento OR g.idInstrumento IS NULL)
            ORDER BY g.idProfesor, g.id
            """)
    List<Grupo> findEspacios(
            @Param("idPeriodo") Long idPeriodo,
            @Param("idInstrumento") Integer idInstrumento,
            @Param("idProfesor") Long idProfesor
    );

    /*
     * Agrupaciones - Grupos de agrupación de un período (o de todos, si
     * idPeriodo es nulo). Usa idx_grupos_periodo_agrupacion.
     */
    @Query("""
            SELECT g FROM Grupo g
            WHERE g.tipoGrupo = 'AGRUPACION'
              AND (:idPeriodo IS NULL OR g.idPeriodo = :idPeriodo)
            ORDER BY g.idPeriodo DESC, g.codigo
            """)
    List<Grupo> findGruposAgrupacion(@Param("idPeriodo") Long idPeriodo);

    // Una agrupación con grupos no se elimina ni cambia de tipo.
    boolean existsByIdAgrupacion(Long idAgrupacion);

    /*
     * Cursos a coordinar en Dirección - Grupos normales (tipo CURSO) de un
     * período cuyo curso tiene a_coordinar = TRUE.
     */
    @Query("""
            SELECT g FROM Grupo g
            WHERE g.tipoGrupo = 'CURSO'
              AND g.idPeriodo = :idPeriodo
              AND g.idCurso IN (SELECT c.id FROM Curso c WHERE c.aCoordinar = true)
            ORDER BY g.codigo
            """)
    List<Grupo> findGruposACoordinar(@Param("idPeriodo") Long idPeriodo);
}