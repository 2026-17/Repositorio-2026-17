package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class CursoRequestDTO {

    @NotBlank
    private String codigo;

    @NotBlank
    private String nombre;

    private String descripcion;

    @NotNull
    @Positive
    private Integer horasTotales;

    // getters y setters
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Integer getHorasTotales() { return horasTotales; }
    public void setHorasTotales(Integer horasTotales) { this.horasTotales = horasTotales; }
}