package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class RegistrarPagoRequest {

    @NotNull(message = "El estudiante es obligatorio.")
    @Positive(message = "El estudiante seleccionado no es válido.")
    private Long idEstudiante;

    @NotNull(message = "El período académico es obligatorio.")
    @Positive(message = "El período académico seleccionado no es válido.")
    private Long idPeriodo;

    @NotNull(message = "El año de la mensualidad es obligatorio.")
    @Min(value = 2000, message = "El año indicado no es válido.")
    @Max(value = 2200, message = "El año indicado no es válido.")
    private Integer anio;

    @NotNull(message = "El mes de la mensualidad es obligatorio.")
    @Min(value = 1, message = "El mes indicado no es válido.")
    @Max(value = 12, message = "El mes indicado no es válido.")
    private Short mes;

    @NotNull(message = "La fecha de pago es obligatoria.")
    private LocalDate fechaPago;

    @Size(max = 1000, message = "Las observaciones no pueden superar los 1000 caracteres.")
    private String observaciones;

    // --- Getters y setters ---

    public Long getIdEstudiante() {
        return idEstudiante;
    }

    public void setIdEstudiante(Long idEstudiante) {
        this.idEstudiante = idEstudiante;
    }

    public Long getIdPeriodo() {
        return idPeriodo;
    }

    public void setIdPeriodo(Long idPeriodo) {
        this.idPeriodo = idPeriodo;
    }

    public Integer getAnio() {
        return anio;
    }

    public void setAnio(Integer anio) {
        this.anio = anio;
    }

    public Short getMes() {
        return mes;
    }

    public void setMes(Short mes) {
        this.mes = mes;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(LocalDate fechaPago) {
        this.fechaPago = fechaPago;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
