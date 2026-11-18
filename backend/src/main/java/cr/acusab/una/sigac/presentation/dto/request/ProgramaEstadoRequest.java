package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// Activar o desactivar un programa académico (no se elimina nunca).
@Getter
@Setter
public class ProgramaEstadoRequest {

    @NotNull(message = "Indique si el programa queda activo o inactivo.")
    private Boolean activo;
}
