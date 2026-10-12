package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cambio obligatorio de la contraseña temporal en el primer inicio de sesión.
 * No pide código por correo: la contraseña temporal ya llegó al correo de la
 * cuenta, así que conocerla demuestra lo mismo.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CambioContrasenaTemporalRequest {

    @NotBlank(message = "La contraseña temporal es obligatoria")
    private String contrasenaActual;

    // El máximo coincide con LoginRequest; si no, la nueva contraseña no serviría para ingresar
    @NotBlank(message = "La contraseña nueva es obligatoria")
    @Size(min = 12, max = 16, message = "La contrasena debe tener entre 12 y 16 caracteres")
    @Pattern(regexp = PoliticaContrasena.REGEX, message = PoliticaContrasena.MENSAJE)
    private String contrasenaNueva;
}
