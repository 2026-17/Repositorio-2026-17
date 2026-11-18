package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/*
 * Modificar un programa académico. El código no se edita: identifica al
 * programa desde su creación (planes y cursos dependen de él).
 */
@Getter
@Setter
public class ProgramaUpdateRequest {

    @NotBlank(message = "El nombre del programa es obligatorio.")
    @Size(max = 150, message = "El nombre del programa no puede superar los 150 caracteres.")
    private String nombre;

    @Size(max = 2000, message = "La descripción no puede superar los 2000 caracteres.")
    private String descripcion;
}
