package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.HorarioGrupo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface HorarioGrupoRepository
        extends JpaRepository<HorarioGrupo, Long> {

    List<HorarioGrupo> findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(
            Collection<Long> idsGrupo
    );
}
