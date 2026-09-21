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
     * Historia 2 - Oferta de grupos de un período, filtrada por estado.
     */
    List<Grupo> findByIdPeriodoAndEstadoIn(
            Long idPeriodo,
            Collection<String> estados
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