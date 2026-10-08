package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Usado tanto para cambio voluntario de contrasena como para el cambio
 * forzado tras una credencial temporal o restablecimiento administrativo
 * (ver 2.3.1 Controles sobre la composicion y asignacion del password).
 * Requiere ademas el codigo enviado al correo del usuario.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CambiarContrasenaRequest {

    @NotBlank
    private String contrasenaActual;

    // El máximo coincide con LoginRequest; si no, la nueva contraseña no serviría para ingresar
    @NotBlank
    @Size(min = 12, max = 16, message = "La contrasena debe tener entre 12 y 16 caracteres")
    @Pattern(regexp = PoliticaContrasena.REGEX, message = PoliticaContrasena.MENSAJE)
    private String contrasenaNueva;

    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos")
    private String codigo;
}
