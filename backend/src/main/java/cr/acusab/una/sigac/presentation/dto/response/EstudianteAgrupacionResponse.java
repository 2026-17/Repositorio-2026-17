package cr.acusab.una.sigac.presentation.dto.response;

/*
 * Agrupaciones - Estudiante matriculado en un grupo de agrupación (vista
 * INTERNA: dirección académica, administración y profesores del grupo).
 *
 * etiqueta: "Orquesta · Conjunto Instrumental V". calificaComoCoro: el
 * detalle se califica como Coro (regla D4) aunque la agrupación sea
 * instrumental.
 */
public record EstudianteAgrupacionResponse(
        Long idEstudiante,
        String numeroIdentificacion,
        String nombreCompleto,
        Long idCursoNivel,
        String nombreNivel,
        String etiqueta,
        boolean calificaComoCoro
) {
}
