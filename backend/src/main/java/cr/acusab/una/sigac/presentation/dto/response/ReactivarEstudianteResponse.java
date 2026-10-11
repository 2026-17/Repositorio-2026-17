package cr.acusab.una.sigac.presentation.dto.response;

public record ReactivarEstudianteResponse(
        Long idEstudiante,
        String estado,
        String mensaje
) {
}
