package cr.acusab.una.sigac.presentation.dto.response;

import cr.acusab.una.sigac.data.entity.TiposEstudiante;

// Tipo de estudiante (REGULAR / INCLUSION): selector y respuestas de estudiantes.
public record TipoEstudianteResponse(
        Integer id,
        String codigo,
        String nombre
) {
    public static TipoEstudianteResponse de(TiposEstudiante tipo) {
        return tipo == null ? null : new TipoEstudianteResponse(tipo.getId(), tipo.getCodigo(), tipo.getNombre());
    }
}
