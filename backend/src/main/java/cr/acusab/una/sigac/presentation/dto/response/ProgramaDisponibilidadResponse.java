package cr.acusab.una.sigac.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * Respuesta de la validación en vivo del asistente "Nuevo programa":
 * indica si el código y el nombre escritos ya están en uso.
 * Un campo no consultado (vacío) se informa como disponible.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProgramaDisponibilidadResponse {

    private boolean codigoDisponible;
    private boolean nombreDisponible;
}
