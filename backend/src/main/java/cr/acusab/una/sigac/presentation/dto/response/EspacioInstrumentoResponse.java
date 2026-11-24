package cr.acusab.una.sigac.presentation.dto.response;

import java.time.LocalTime;

/*
 * Espacios de instrumento - Un espacio de la agenda de un profesor.
 * Libre: ocupado = false y sin estudiante ni nivel.
 * Ocupado: estudiante que lo tomó y nivel que cursa en él.
 *
 * Espacio de "cualquier instrumento del profesor": idInstrumento = null,
 * variosInstrumentos = true y nombreInstrumento con los instrumentos del
 * profesor unidos con " o " ("Violín o Viola").
 *
 * idInstrumentoEstudiante / nombreInstrumentoEstudiante: el instrumento
 * que cursa el estudiante que lo ocupa (null si está libre).
 */
public record EspacioInstrumentoResponse(
        Long idGrupo,
        String codigo,
        Long idPeriodo,
        String nombrePeriodo,
        Integer idInstrumento,
        String nombreInstrumento,
        Long idProfesor,
        String nombreProfesor,
        Short diaSemana,
        LocalTime horaInicio,
        LocalTime horaFin,
        String aula,
        String estado,
        boolean ocupado,
        Long idEstudiante,
        String nombreEstudiante,
        Long idCursoNivel,
        String nombreNivel,
        boolean variosInstrumentos,
        Integer idInstrumentoEstudiante,
        String nombreInstrumentoEstudiante
) {
}
