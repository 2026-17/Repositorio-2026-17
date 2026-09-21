package cr.acusab.una.sigac.presentation.dto.response;

import java.time.LocalDate;

/*
 * HU-026 - Representa una fila del historial de instrumentos
 * del estudiante (una asignación, vigente o cerrada).
 */
public record InstrumentoEstudianteResponse(
        Long idEstudianteInstrumento,
        Long idEstudiante,
        Integer idInstrumento,
        String codigoInstrumento,
        String nombreInstrumento,
        Long idProfesor,
        String nombreProfesor,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        boolean vigente,
        String observaciones
) {}
