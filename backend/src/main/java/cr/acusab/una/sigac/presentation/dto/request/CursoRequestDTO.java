package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class CursoRequestDTO {

    // --- Datos del curso ---
    @NotBlank
    private String codigo;

    @NotBlank
    private String nombre;

    private String descripcion;

    @NotNull
    @Positive
    private Integer horasTotales;

    // --- Ubicación académica ---
    @NotNull
    private Long idPrograma;

    @NotNull
    @Positive
    private Short nivel;

    // --- Primera oferta del curso (grupo) ---
    @NotNull
    @Positive
    private Short cupos;

    @NotNull
    private Long idPeriodo;

    // getters y setters
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Integer getHorasTotales() { return horasTotales; }
    public void setHorasTotales(Integer horasTotales) { this.horasTotales = horasTotales; }
    public Long getIdPrograma() { return idPrograma; }
    public void setIdPrograma(Long idPrograma) { this.idPrograma = idPrograma; }
    public Short getNivel() { return nivel; }
    public void setNivel(Short nivel) { this.nivel = nivel; }
    public Short getCupos() { return cupos; }
    public void setCupos(Short cupos) { this.cupos = cupos; }
    public Long getIdPeriodo() { return idPeriodo; }
    public void setIdPeriodo(Long idPeriodo) { this.idPeriodo = idPeriodo; }
}