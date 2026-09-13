package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CursoRequestDTO {

    // --- Datos del curso ---

    @NotBlank(message = "El código del curso es obligatorio.")
    @Size(max = 30, message = "El código del curso no puede superar los 30 caracteres.")
    private String codigo;

    @NotBlank(message = "El nombre del curso es obligatorio.")
    @Size(max = 160, message = "El nombre del curso no puede superar los 160 caracteres.")
    private String nombre;

    private String descripcion;

    @NotNull(message = "La cantidad de horas del curso es obligatoria.")
    @Positive(message = "La cantidad de horas debe ser mayor que cero.")
    @Max(value = 65535, message = "La cantidad de horas supera el máximo permitido.")
    private Integer horasTotales;

    // --- Ubicación académica ---

    @NotNull(message = "El programa académico es obligatorio.")
    @Positive(message = "El programa académico seleccionado no es válido.")
    private Long idPrograma;

    @NotNull(message = "El nivel académico es obligatorio.")
    @Positive(message = "El nivel académico debe ser mayor que cero.")
    private Short nivel;

    // --- Primera oferta del curso ---

    @NotNull(message = "La cantidad de cupos es obligatoria.")
    @Positive(message = "La cantidad de cupos debe ser mayor que cero.")
    private Short cupos;

    @NotNull(message = "El período lectivo es obligatorio.")
    @Positive(message = "El período lectivo seleccionado no es válido.")
    private Long idPeriodo;

    // --- Getters y setters ---

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

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

    public Short getCupos() {
        return cupos;
    }

    public void setCupos(Short cupos) {
        this.cupos = cupos;
    }

    public Long getIdPeriodo() {
        return idPeriodo;
    }

    public void setIdPeriodo(Long idPeriodo) {
        this.idPeriodo = idPeriodo;
    }
}