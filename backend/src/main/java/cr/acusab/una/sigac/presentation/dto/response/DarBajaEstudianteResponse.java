package cr.acusab.una.sigac.presentation.dto.response;

import java.time.Instant;

public record DarBajaEstudianteResponse(
        Long idEstudiante,
        String estado,
        Instant fechaBaja,
        String motivoBaja,
        int matriculasCanceladas,
        int cursosRetirados
) {
}
