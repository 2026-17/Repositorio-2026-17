package cr.acusab.una.sigac.presentation.dto.response;

import java.time.LocalTime;
import java.util.List;

/*
 * Agrupaciones - Grupo de una agrupación en un período (vista de la
 * dirección académica).
 *
 * choques: avisos (no bloquean) de bloques que chocan con otros grupos o
 * espacios de alguno de sus profesores en ese período. Solo vienen al
 * guardar o al revisar el horario.
 */
public record GrupoAgrupacionResponse(
        Long idGrupo,
        String codigo,
        Long idPeriodo,
        String nombrePeriodo,
        Long idAgrupacion,
        String nombreAgrupacion,
        String tipoAgrupacion,
        int cupoMaximo,
        long matriculados,
        int cupoDisponible,
        String estado,
        String aula,
        List<Horario> horarios,
        List<Profesor> profesores,
        List<AgrupacionResponse.Instrumento> instrumentosPermitidos,
        List<Choque> choques,
        // ¿Quiénes pueden matricularla? (de la agrupación)
        boolean soloInclusion,
        Integer edadMinima,
        Integer edadMaxima
) {

    public record Horario(Short diaSemana, LocalTime horaInicio, LocalTime horaFin, String aula) {
    }

    public record Profesor(Long idProfesor, String nombre, boolean responsable, boolean activo) {
    }

    /*
     * indiceBloque: posición del bloque en la lista enviada (desde 0).
     * mensaje: "Ana Mora ya tiene el espacio ESP-PIA-12-3 el lunes de
     * 4:00 p. m. a 4:40 p. m."
     */
    public record Choque(int indiceBloque, Long idProfesor, String nombreProfesor, String codigoGrupo, String mensaje) {
    }
}
