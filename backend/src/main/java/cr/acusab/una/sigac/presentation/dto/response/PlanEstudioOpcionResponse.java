package cr.acusab.una.sigac.presentation.dto.response;

/*
 * Plan de estudio vigente, para seleccionarlo en el registro y la edición
 * de estudiantes.
 */
public record PlanEstudioOpcionResponse(
        Long id,
        String codigo,
        String nombre,
        String version
) {}
