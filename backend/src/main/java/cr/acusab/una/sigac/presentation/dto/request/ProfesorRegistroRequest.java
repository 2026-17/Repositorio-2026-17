package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Envolvente que agrupa los datos del profesor y los datos de la cuenta de
 * usuario que se le debe crear (ver HU-013 Registrar y modificar profesores).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProfesorRegistroRequest {

    @NotNull(message = "Los datos del profesor son obligatorios")
    @Valid
    private ProfesorCreateRequest profesor;

    @NotNull(message = "Los datos de la cuenta de usuario son obligatorios")
    @Valid
    private UsuarioCreateRequest usuario;
}
