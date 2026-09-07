package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Usado tanto para cambio voluntario de contrasena como para el cambio
 * forzado tras una credencial temporal o restablecimiento administrativo
 * (ver 2.3.1 Controles sobre la composicion y asignacion del password).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CambiarContrasenaRequest {

    @NotBlank
    private String contrasenaActual;

    @NotBlank
    @Size(min = 12, message = "La contrasena debe tener al menos 12 caracteres")
    private String contrasenaNueva;
}
