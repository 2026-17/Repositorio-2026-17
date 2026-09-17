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