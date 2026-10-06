package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Horario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HorarioRepository extends JpaRepository<Horario, Long> {

    List<Horario> findByIdGrupo_Id(Long idGrupo);

    // Trae de una sola vez los horarios de varios grupos (chequeo de conflictos).
    List<Horario> findByIdGrupo_IdIn(List<Long> idsGrupo);

    void deleteByIdGrupo_Id(Long idGrupo);
}
