package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DarBajaEstudianteRequest(
        @NotBlank(message = "El motivo de baja es obligatorio.")
        @Size(max = 500, message = "El motivo de baja no puede superar 500 caracteres.")
        String motivoBaja
) {
}
