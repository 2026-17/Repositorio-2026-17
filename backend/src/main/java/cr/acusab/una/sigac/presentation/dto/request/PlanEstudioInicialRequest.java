package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/*
 * Datos del plan de estudio que se crea junto con un programa académico
 * (o después, si el programa quedó sin plan vigente).
 *
 * El código y el nombre del plan los genera el sistema a partir del
 * programa y la versión, para que el director no tenga que inventarlos.
 */
@Getter
@Setter
public class PlanEstudioInicialRequest {

    @NotBlank(message = "La versión del plan de estudio es obligatoria.")
    @Size(max = 20, message = "La versión del plan no puede superar los 20 caracteres.")
    @Pattern(
            regexp = "^[0-9A-Za-z][0-9A-Za-z.\\-]*$",
            message = "La versión solo puede tener letras, números, puntos y guiones (ej. 1.0)."
    )
    private String version;

    @NotNull(message = "La fecha de inicio de vigencia del plan es obligatoria.")
    private LocalDate fechaVigenciaDesde;

    @NotNull(message = "La nota mínima de aprobación es obligatoria.")
    @DecimalMin(value = "0.00", message = "La nota mínima de aprobación debe estar entre 0 y 100.")
    @DecimalMax(value = "100.00", message = "La nota mínima de aprobación debe estar entre 0 y 100.")
    @Digits(integer = 3, fraction = 2, message = "La nota mínima admite como máximo dos decimales.")
    private BigDecimal notaMinimaAprobacion;
}
