package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos utilizados para crear o modificar un contacto de emergencia
 * durante la actualización de un encargado en la HU-023.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContactoEmergenciaUpdateRequest {

    /*
     * Si contiene un ID, se modifica el contacto existente.
     * Si es null, se considera un contacto nuevo.
     */
    private Long idContactoEmergencia;

    @NotBlank
    @Size(max = 180)
    private String nombre;

    @NotBlank
    @Size(max = 30)
    private String telefono;

    @NotBlank
    @Size(max = 80)
    private String relacion;
}