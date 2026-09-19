package cr.acusab.una.sigac.presentation.dto.response;

public record EstudianteListadoResponse(
        Long id,
        String nombreCompleto,
        String numeroIdentificacion,
        int edad,
        String nivelEscolar,
        String instrumento,
        String centroEducativo,
        boolean activo
) {}
