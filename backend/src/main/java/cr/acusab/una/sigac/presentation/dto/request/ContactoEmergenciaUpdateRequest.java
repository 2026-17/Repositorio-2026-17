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

    @NotBlank(message = "El nombre del contacto es obligatorio")
    @Size(max = 180, message = "El nombre del contacto no puede superar 180 caracteres")
    private String nombre;

    @NotBlank(message = "El teléfono del contacto es obligatorio")
    @Size(max = 30, message = "El teléfono del contacto no puede superar 30 caracteres")
    private String telefono;

    @NotBlank(message = "La relación con el estudiante es obligatoria")
    @Size(max = 80, message = "La relación no puede superar 80 caracteres")
    private String relacion;
}