package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/*
 * Varios pagos de un mismo período con un solo comprobante: los meses
 * marcados y, opcionalmente, el pago de matrícula (ej. un estudiante
 * que paga dos meses seguidos, o la matrícula junto con el primer mes).
 */
@Getter
@Setter
public class RegistrarPagosRequest {

    @NotNull(message = "El estudiante es obligatorio.")
    @Positive(message = "El estudiante seleccionado no es válido.")
    private Long idEstudiante;

    @NotNull(message = "El período académico es obligatorio.")
    @Positive(message = "El período académico seleccionado no es válido.")
    private Long idPeriodo;

    @Size(max = 24, message = "Se indicaron demasiados meses.")
    private List<@Valid MesPago> meses = new ArrayList<>();

    private boolean incluirMatricula;

    @NotNull(message = "La fecha de pago es obligatoria.")
    private LocalDate fechaPago;

    @Size(max = 1000, message = "Las observaciones no pueden superar los 1000 caracteres.")
    private String observaciones;

    @Getter
    @Setter
    public static class MesPago {

        @NotNull(message = "El año de la mensualidad es obligatorio.")
        @Min(value = 2000, message = "El año indicado no es válido.")
        @Max(value = 2200, message = "El año indicado no es válido.")
        private Integer anio;

        @NotNull(message = "El mes de la mensualidad es obligatorio.")
        @Min(value = 1, message = "El mes indicado no es válido.")
        @Max(value = 12, message = "El mes indicado no es válido.")
        private Short mes;
    }
}
