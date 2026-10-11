package cr.acusab.una.sigac.presentation.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AsignacionProfesorResponse(
        Long idGrupo,
        String codigoGrupo,
        Long idCurso,
        String codigoCurso,
        String nombreCurso,
        Long idPeriodo,
        String nombrePeriodo,
        LocalDate inicioPeriodo,
        Long idProfesor,
        String nombreProfesor,
        boolean cursoActivo,
        List<HorarioResponse> horarios
) {
    public record HorarioResponse(
            Long idHorario,
            Short diaSemana,
            LocalTime horaInicio,
            LocalTime horaFin
    ) {
    }
}
