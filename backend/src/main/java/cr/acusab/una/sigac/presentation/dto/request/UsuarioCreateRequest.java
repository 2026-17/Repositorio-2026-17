package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static cr.acusab.una.sigac.presentation.dto.request.PoliticaDatosPersona.*;

/**
 * Datos requeridos para que un Administrador registre una nueva cuenta
 * (ver 2.3.2 Registro de usuarios nuevos).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioCreateRequest {

    // La cuenta no tiene tipo de identificación: solo se exige letras y números
    @NotBlank(message = "La identificación es obligatoria")
    @Size(max = 40, message = "La identificación no puede superar 40 caracteres")
    @Pattern(regexp = IDENTIFICACION_USUARIO_REGEX, message = IDENTIFICACION_USUARIO_MENSAJE)
    private String cedula;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    @Pattern(regexp = NOMBRE_REGEX, message = NOMBRE_MENSAJE)
    private String nombre;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = CORREO_MENSAJE)
    @Pattern(regexp = CORREO_REGEX, message = CORREO_MENSAJE)
    @Size(max = 254, message = "El correo no puede superar 254 caracteres")
    private String correo;

    // Sin contraseña: el sistema genera una temporal y la envía al correo
    // (ver ContrasenaTemporalService).

    @NotNull(message = "El rol es obligatorio")
    private Integer idRol;

    @NotNull(message = "El estado del usuario es obligatorio")
    private Integer idEstadoUsuario;
}
