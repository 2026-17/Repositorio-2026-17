package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos de un contacto de emergencia asociado al expediente
 * de un estudiante en la HU-023 Registrar encargados.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContactoEmergenciaRequest {

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