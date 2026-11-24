package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.EstudiantesInstrumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EstudianteInstrumentoRepository extends JpaRepository<EstudiantesInstrumento, Long> {


    // HU-015 - Cantidad de instrumentos vigentes de un estudiante,
    // usada para calcular el monto de la mensualidad.
    long countByIdEstudiante_IdAndFechaFinIsNull(Long idEstudiante);
    /*
     * HU-026 - Historial completo de instrumentos del estudiante,
     * del mas reciente al mas antiguo.
     */
    List<EstudiantesInstrumento> findByIdEstudiante_IdOrderByFechaInicioDesc(Long idEstudiante);

    /*
     * HU-026 - Instrumentos actualmente vigentes del estudiante
     * (sin fecha_fin registrada).
     */
    List<EstudiantesInstrumento> findByIdEstudiante_IdAndFechaFinIsNull(Long idEstudiante);

    /*
     * HU-026 - Evita asignar dos veces el mismo instrumento vigente
     * (misma pareja estudiante-instrumento sin fecha_fin).
     */
    Optional<EstudiantesInstrumento> findByIdEstudiante_IdAndIdInstrumento_IdAndFechaFinIsNull(
            Long idEstudiante, Integer idInstrumento);

    /*
     * HU-026 - Registro del mismo estudiante e instrumento que empezó en esa
     * fecha. La tabla solo admite uno (uq_estudiantes_instrumentos_periodo),
     * así que si se finalizó hoy y se vuelve a asignar hoy, se reabre ese.
     */
    Optional<EstudiantesInstrumento> findByIdEstudiante_IdAndIdInstrumento_IdAndFechaInicio(
            Long idEstudiante, Integer idInstrumento, LocalDate fechaInicio);
}
