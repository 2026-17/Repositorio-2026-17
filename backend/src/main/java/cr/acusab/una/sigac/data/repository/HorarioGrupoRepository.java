package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.HorarioGrupo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface HorarioGrupoRepository
        extends JpaRepository<HorarioGrupo, Long> {

    List<HorarioGrupo> findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(
            Collection<Long> idsGrupo
    );

    /*
     * Modificar grupo: el horario se reemplaza completo. El borrado se
     * ejecuta de inmediato (no al final de la transacción) para que los
     * bloques nuevos no choquen con uq_horarios_grupos.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM HorarioGrupo h WHERE h.idGrupo = :idGrupo")
    void eliminarPorGrupo(@Param("idGrupo") Long idGrupo);
}
