package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CursoUpdateRequestDTO {

    @NotBlank(message = "El nombre del curso es obligatorio.")
    @Size(max = 160, message = "El nombre del curso no puede superar los 160 caracteres.")
    private String nombre;

    private String descripcion;

    @NotNull(message = "La cantidad de horas es obligatoria.")
    @Positive(message = "La cantidad de horas debe ser mayor que cero.")
    @Max(value = 65535, message = "La cantidad de horas no puede superar 65535.")
    private Integer horasTotales;

    @NotNull(message = "El programa académico es obligatorio.")
    @Positive(message = "El programa académico seleccionado no es válido.")
    private Long idPrograma;

    @NotNull(message = "El nivel académico es obligatorio.")
    @Positive(message = "El nivel académico debe ser mayor que cero.")
    @Max(value = 65535, message = "El nivel académico no puede superar 65535.")
    private Short nivel;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Integer getHorasTotales() {
        return horasTotales;
    }

    public void setHorasTotales(Integer horasTotales) {
        this.horasTotales = horasTotales;
    }

    public Long getIdPrograma() {
        return idPrograma;
    }

    public void setIdPrograma(Long idPrograma) {
        this.idPrograma = idPrograma;
    }

    public Short getNivel() {
        return nivel;
    }

    public void setNivel(Short nivel) {
        this.nivel = nivel;
    }
}