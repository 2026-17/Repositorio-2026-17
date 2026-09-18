package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Beca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BecaRepository extends JpaRepository<Beca, Long> {

    List<Beca> findByIdEstudiante_IdOrderByFechaInicioDesc(Long idEstudiante);

    List<Beca> findByIdEstudiante_IdAndActivaTrue(Long idEstudiante);

    Optional<Beca> findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(Long idEstudiante, Long idPeriodo);

    boolean existsByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(Long idEstudiante, Long idPeriodo);
}
