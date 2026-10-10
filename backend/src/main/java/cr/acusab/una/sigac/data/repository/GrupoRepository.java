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

    List<Grupo> findByIdProfesorAndEstadoIn(
            Long idProfesor,
            Collection<String> estados
    );

    List<Grupo> findByIdCursoAndIdProfesorIsNotNull(Long idCurso);

    boolean existsByIdCursoAndIdProfesorIsNotNull(Long idCurso);

    /*
     * HU-014: incluye grupos del profesor en cualquier periodo cuyas fechas
     * se traslapen con el periodo del grupo destino. No limita la validacion
     * al mismo id de periodo, porque dos periodos pueden coincidir en fechas.
     */
    @Query("""
            SELECT g FROM Grupo g, PeriodoAcademico periodoGrupo, PeriodoAcademico periodoDestino
            WHERE g.idProfesor = :idProfesor
              AND g.idPeriodo = periodoGrupo.id
              AND periodoDestino.id = :idPeriodo
              AND periodoGrupo.fechaInicio <= periodoDestino.fechaFin
              AND periodoDestino.fechaInicio <= periodoGrupo.fechaFin
              AND g.estado NOT IN ('CANCELADO', 'CERRADO')
            """)
    List<Grupo> findGruposProfesorEnPeriodo(
            @Param("idProfesor") Long idProfesor,
            @Param("idPeriodo") Long idPeriodo
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
}