package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/*
 * Historia 2 - Grupos que el estudiante confirma matricular.
 *
 * El estudiante y el período no se envían: se obtienen de la sesión
 * activa y del período académico vigente.
 */
@Getter
@Setter
public class ConfirmarSeleccionMatriculaRequestDTO {

    @NotEmpty(message = "Debe seleccionar al menos un curso.")
    private List<Long> idsGrupo;
}
