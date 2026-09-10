package cr.acusab.una.sigac.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * DTO de salida para Usuario. Nunca incluye hashContrasena, intentosFallidos
 * ni bloqueadoHasta: son datos internos de seguridad (RNF-001, RNF-008).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {
    private Long id;
    private String nombre;
    private String correo;
    private RoleResponse rol;
    private EstadoUsuarioResponse estado;
    private Boolean requiereCambioContrasena;
    private Instant ultimoAcceso;
    private Instant creadoEn;
}
