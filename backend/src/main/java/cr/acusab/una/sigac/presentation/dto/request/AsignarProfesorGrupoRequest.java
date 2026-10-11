package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AsignarProfesorGrupoRequest(
        @NotNull(message = "El profesor es obligatorio.")
        @Positive(message = "El profesor seleccionado no es válido.")
        Long idProfesor
) {
}
