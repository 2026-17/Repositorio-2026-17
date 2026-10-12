package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Permite a un Administrador cambiar el rol o el estado de una cuenta
 * existente. No modifica la contrasena (ver CambiarContrasenaRequest).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioUpdateRequest {

    @NotNull(message = "El rol es obligatorio")
    private Integer idRol;

    @NotNull(message = "El estado del usuario es obligatorio")
    private Integer idEstadoUsuario;
}
