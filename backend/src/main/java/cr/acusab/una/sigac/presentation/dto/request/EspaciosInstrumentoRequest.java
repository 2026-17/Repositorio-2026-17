package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/*
 * Espacios de instrumento - Crear en lote los espacios semanales de un
 * profesor en un instrumento y un período. Cada bloque es un espacio
 * (lección individual, cupo 1).
 */
@Getter
@Setter
public class EspaciosInstrumentoRequest {

    @NotNull(message = "El período académico es obligatorio.")
    @Positive(message = "El período académico seleccionado no es válido.")
    private Long idPeriodo;

    @NotNull(message = "El instrumento es obligatorio.")
    @Positive(message = "El instrumento seleccionado no es válido.")
    private Integer idInstrumento;

    @NotNull(message = "El profesor es obligatorio.")
    @Positive(message = "El profesor seleccionado no es válido.")
    private Long idProfesor;

    // Aula por defecto de los bloques que no indiquen otra.
    @Size(max = 80, message = "El aula no puede superar los 80 caracteres.")
    private String aula;

    /*
     * El cupo de un espacio siempre es 1. Se acepta en la solicitud solo
     * para rechazar con un mensaje claro cualquier otro valor.
     */
    private Integer cupoMaximo;

    @Valid
    @NotEmpty(message = "Agregue al menos un espacio.")
    private List<HorarioGrupoRequestDTO> bloques = new ArrayList<>();
}
