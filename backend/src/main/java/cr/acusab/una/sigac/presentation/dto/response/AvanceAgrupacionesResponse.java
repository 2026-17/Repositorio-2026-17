package cr.acusab.una.sigac.presentation.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/*
 * Agrupaciones - Avance de un estudiante en cada agrupación (vista
 * INTERNA). Una fila por agrupación y familia (Conjunto Instrumental o
 * Coro). idAgrupacion = null es el historial sin agrupación de la carga
 * inicial (punto de partida, regla D2).
 */
public record AvanceAgrupacionesResponse(Long idEstudiante, List<Avance> avances, List<Familia> familias) {

    /*
     * D5: avance de cada familia para el plan = el de la agrupación más
     * adelantada. completa: tiene todos los niveles en UNA MISMA agrupación.
     * etiquetaUltimo: "Orquesta XII" (null si no ha aprobado ninguno).
     */
    public record Familia(
            String familia,
            String nombreFamilia,
            Long idAgrupacion,
            String nombreAgrupacion,
            int ultimoNivel,
            int nivelMaximo,
            String etiquetaUltimo,
            boolean completa
    ) {
    }

    public record Avance(
            Long idAgrupacion,
            String nombreAgrupacion,
            String tipoAgrupacion,
            String familia,
            String nombreFamilia,
            List<NivelAprobado> aprobados,
            Long idCursoSiguiente,
            String nombreCursoSiguiente,
            String etiquetaSiguiente,
            boolean completado,
            String motivo
    ) {
    }

    // origen: SIGAC, CERTIFICACION o NIVEL_INGRESO.
    public record NivelAprobado(
            Long idCurso,
            String nombreCurso,
            String etiqueta,
            String origen,
            Long idPeriodo,
            String nombrePeriodo,
            LocalDate fecha,
            BigDecimal nota
    ) {
    }
}
