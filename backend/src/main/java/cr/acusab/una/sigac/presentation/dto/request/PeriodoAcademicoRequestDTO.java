package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class PeriodoAcademicoRequestDTO {

    @NotBlank(message = "El nombre del período académico es obligatorio.")
    @Size(max = 100, message = "El nombre del período académico no puede superar los 100 caracteres.")
    private String nombre;

    @NotNull(message = "La fecha de inicio del período es obligatoria.")
    private LocalDate fechaInicio;

    @NotNull(message = "La fecha de fin del período es obligatoria.")
    private LocalDate fechaFin;

    private LocalDate fechaInicioMatricula;

    private LocalDate fechaFinMatricula;

    @NotBlank(message = "El estado del período académico es obligatorio.")
    private String estado;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDate fechaFin) {
        this.fechaFin = fechaFin;
    }

    public LocalDate getFechaInicioMatricula() {
        return fechaInicioMatricula;
    }

    public void setFechaInicioMatricula(LocalDate fechaInicioMatricula) {
        this.fechaInicioMatricula = fechaInicioMatricula;
    }

    public LocalDate getFechaFinMatricula() {
        return fechaFinMatricula;
    }

    public void setFechaFinMatricula(LocalDate fechaFinMatricula) {
        this.fechaFinMatricula = fechaFinMatricula;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
