package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.AutorizacionesMatricula;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AutorizacionesMatriculaRepository
        extends JpaRepository<AutorizacionesMatricula, Long> {

    /*
     * HU-012 - Solicitudes pendientes de revisión.
     */
    List<AutorizacionesMatricula>
    findByIdEstadoAutorizacion_CodigoIgnoreCaseOrderByCreadoEnAsc(
            String codigo
    );

    /*
     * HU-012 - Historial de solicitudes ya procesadas.
     *
     * Permite consultar, por ejemplo:
     * - AUTORIZADA
     * - RECHAZADA
     *
     * Las más recientemente revisadas aparecen primero.
     */
    List<AutorizacionesMatricula>
    findByIdEstadoAutorizacion_CodigoInOrderByFechaRevisionDesc(
            List<String> codigos
    );

    /*
     * Busca la autorización correspondiente a un estudiante
     * dentro de un período académico específico.
     */
    Optional<AutorizacionesMatricula>
    findByIdEstudiante_IdAndIdPeriodo_Id(
            Long idEstudiante,
            Long idPeriodo
    );

    /*
     * Horario del estudiante: todas sus autorizaciones, para saber en
     * qué períodos tiene cursos matriculados.
     */
    List<AutorizacionesMatricula>
    findByIdEstudiante_Id(
            Long idEstudiante
    );
}