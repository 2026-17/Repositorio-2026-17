package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CursoRequestDTO {
    // Omitir este apartado conserva los requisitos existentes.
    @jakarta.validation.Valid
    private ConfiguracionRequisitosCursoRequest requisitos;
    public ConfiguracionRequisitosCursoRequest getRequisitos() { return requisitos; }
    public void setRequisitos(ConfiguracionRequisitosCursoRequest value) { requisitos = value; }


    // --- Datos del curso ---

    @NotBlank(message = "El código del curso es obligatorio.")
    @Size(max = 30, message = "El código del curso no puede superar los 30 caracteres.")
    private String codigo;

    @NotBlank(message = "El nombre del curso es obligatorio.")
    @Size(max = 160, message = "El nombre del curso no puede superar los 160 caracteres.")
    private String nombre;

    @Size(max = 2000, message = "La descripción no puede superar los 2000 caracteres.")
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
    @Max(value = 999, message = "El nivel académico no puede ser mayor que 999.")
    private Short nivel;

    /*
     * El curso no lleva período ni cupos: es parte del catálogo y se
     * repite todos los años. Su oferta en cada período (cupo, profesor,
     * horario) es un grupo y se abre desde la pantalla Grupos.
     */

    /*
     * Se cursa en espacios de instrumento (Instrumento I a XIV): no se
     * abre como grupo normal. Omitido = false.
     */
    private Boolean esCursoInstrumento;

    public Boolean getEsCursoInstrumento() {
        return esCursoInstrumento;
    }

    public void setEsCursoInstrumento(Boolean esCursoInstrumento) {
        this.esCursoInstrumento = esCursoInstrumento;
    }

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
}