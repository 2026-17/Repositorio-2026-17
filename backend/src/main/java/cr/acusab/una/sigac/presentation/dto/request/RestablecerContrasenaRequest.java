package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Segundo paso de "Olvidé mi contraseña": el usuario envía el código que
 * recibió por correo junto con su nueva contraseña.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RestablecerContrasenaRequest {

    @NotBlank(message = "La cédula es obligatoria")
    @Size(max = 20, message = "La cédula no puede superar 20 caracteres")
    private String cedula;

    @NotBlank(message = "El código es obligatorio")
    @Pattern(regexp = "\\d{6}", message = "El código debe tener 6 dígitos")
    private String codigo;

    // El máximo coincide con LoginRequest; si no, la nueva contraseña no serviría para ingresar
    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 12, max = 16, message = "La contraseña debe tener entre 12 y 16 caracteres")
    @Pattern(regexp = PoliticaContrasena.REGEX, message = PoliticaContrasena.MENSAJE)
    private String contrasenaNueva;
}
