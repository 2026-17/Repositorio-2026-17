package cr.acusab.una.sigac.presentation.dto.response;

import java.time.Instant;

/**
 * Contraseña temporal generada por la administración (sin correo). Se
 * muestra una sola vez en pantalla.
 */
public record ContrasenaTemporalResponse(
        String contrasenaTemporal,
        Instant generadaEn
) {
    // Para que la contraseña nunca termine en un log por accidente
    @Override
    public String toString() {
        return "ContrasenaTemporalResponse[contrasenaTemporal=****, generadaEn=" + generadaEn + "]";
    }
}
