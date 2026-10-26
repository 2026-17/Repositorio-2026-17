package cr.acusab.una.sigac.presentation.dto.response;

public record ProfesorAsignableResponse(
        Long idProfesor,
        String numeroIdentificacion,
        String nombreCompleto,
        String especialidad,
        Boolean activo
) {
}
