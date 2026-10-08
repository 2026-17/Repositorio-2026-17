package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos requeridos para que un Administrador registre una nueva cuenta
 * (ver 2.3.2 Registro de usuarios nuevos).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioCreateRequest {

    @NotBlank
    @Size(max = 40)
    private String cedula;

    @NotBlank
    @Size(max = 150)
    private String nombre;

    @NotBlank
    @Email
    @Size(max = 254)
    private String correo;

    // Sin contraseña: el sistema genera una temporal y la envía al correo
    // (ver ContrasenaTemporalService).

    @NotNull
    private Integer idRol;

    @NotNull
    private Integer idEstadoUsuario;

}
