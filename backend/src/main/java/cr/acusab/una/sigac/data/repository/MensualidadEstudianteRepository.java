package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.MensualidadesEstudiante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MensualidadEstudianteRepository extends JpaRepository<MensualidadesEstudiante, Long> {

    Optional<MensualidadesEstudiante> findByIdEstudiante_IdAndAnioAndMes(
            Long idEstudiante, Integer anio, Short mes);

    List<MensualidadesEstudiante> findByIdEstudiante_IdOrderByAnioDescMesDesc(Long idEstudiante);
}
