package cr.acusab.una.sigac.presentation.dto.response;

import java.time.LocalDate;
import java.util.List;

/*
 * Avance por instrumento de un estudiante: por cada instrumento vigente,
 * su profesor, si es el instrumento del plan o un extra, y los niveles
 * Instrumento I..XIV de la malla con su estado.
 */
public record AvanceInstrumentosResponse(
        Long idEstudiante,
        Long idPlanEstudio,
        String nombrePlan,
        List<Instrumento> instrumentos
) {

    public record Instrumento(
            Long idEstudianteInstrumento,
            Integer idInstrumento,
            String nombreInstrumento,
            Long idProfesor,
            String nombreProfesor,
            boolean delPlan,
            Long idCursoSiguiente,
            String nombreCursoSiguiente,
            // Ya aprobó o convalidó todos los niveles del plan.
            boolean completado,
            // Hay niveles aprobados con calificación de SIGAC (no admite nivel de ingreso).
            boolean tieneAprobadosEnSigac,
            List<Nivel> niveles
    ) {
    }

    /*
     * estado: APROBADO, CONVALIDADO, SIGUIENTE o PENDIENTE.
     * origen: SIGAC, CERTIFICACION o NIVEL_INGRESO (solo si está aprobado
     * o convalidado). Con origen SIGAC viene el período; con los demás, la
     * fecha del reconocimiento.
     */
    public record Nivel(
            Long idCurso,
            String codigo,
            String nombre,
            int numero,
            String estado,
            String origen,
            Long idPeriodo,
            String nombrePeriodo,
            LocalDate fecha
    ) {
    }
}
