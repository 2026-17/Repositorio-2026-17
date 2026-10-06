package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Primer paso de "Olvidé mi contraseña": el usuario indica su cédula y,
 * si está registrada, se envía un código al correo de la cuenta.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SolicitarRecuperacionRequest {

    @NotBlank(message = "La cédula es obligatoria")
    @Size(max = 20, message = "La cédula no puede superar 20 caracteres")
    private String cedula;
}
