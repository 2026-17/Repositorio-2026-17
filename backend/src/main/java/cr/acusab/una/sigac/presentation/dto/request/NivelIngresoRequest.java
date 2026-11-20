package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/*
 * Nivel de ingreso de un instrumento (prueba de ubicación). Los niveles
 * anteriores quedan CONVALIDADOS y el estudiante empieza en este.
 */
@Getter
@Setter
public class NivelIngresoRequest {

    // Curso de instrumento en el que ingresa (por ejemplo, Instrumento III).
    @NotNull(message = "Seleccione el nivel de ingreso.")
    @Positive(message = "El nivel de ingreso seleccionado no es válido.")
    private Long idCurso;

    @NotBlank(message = "Indique el motivo del nivel de ingreso (por ejemplo, el resultado de la prueba de ubicación).")
    @Size(max = 500, message = "El motivo no puede superar los 500 caracteres.")
    private String motivo;
}
