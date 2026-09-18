package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BecaActualizarRequest {

    @NotNull(message = "El tipo de beca es obligatorio.")
    @Positive(message = "El tipo de beca seleccionado no es válido.")
    private Integer idTipoBeca;

    @NotNull(message = "El período académico es obligatorio.")
    @Positive(message = "El período académico seleccionado no es válido.")
    private Long idPeriodo;

    @NotNull(message = "El porcentaje de beca es obligatorio.")
    private BigDecimal porcentaje;

    private LocalDate fechaFin;

    @Size(max = 1000, message = "El motivo no puede superar los 1000 caracteres.")
    private String motivo;

    // --- Getters y setters ---

    public Integer getIdTipoBeca() {
        return idTipoBeca;
    }

    public void setIdTipoBeca(Integer idTipoBeca) {
        this.idTipoBeca = idTipoBeca;
    }

    public Long getIdPeriodo() {
        return idPeriodo;
    }

    public void setIdPeriodo(Long idPeriodo) {
        this.idPeriodo = idPeriodo;
    }

    public BigDecimal getPorcentaje() {
        return porcentaje;
    }

    public void setPorcentaje(BigDecimal porcentaje) {
        this.porcentaje = porcentaje;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDate fechaFin) {
        this.fechaFin = fechaFin;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
