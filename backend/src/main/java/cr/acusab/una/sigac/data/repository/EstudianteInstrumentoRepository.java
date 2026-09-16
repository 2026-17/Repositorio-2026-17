package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.EstudiantesInstrumento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstudianteInstrumentoRepository extends JpaRepository<EstudiantesInstrumento, Long> {

    // HU-015 - Cantidad de instrumentos vigentes de un estudiante,
    // usada para calcular el monto de la mensualidad.
    long countByIdEstudiante_IdAndFechaFinIsNull(Long idEstudiante);
}
