package cr.acusab.una.sigac.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class LoginResponse {
    private final String token;
    private final long expiration;
    private final String rol;
    private final String nombre;
    private final String correo;
    // true si ingresó con la contraseña temporal: debe cambiarla antes de usar el sistema
    private final boolean requiereCambioContrasena;
}