package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/*
 * HU-030 - Modificar curso de la malla curricular.
 *
 * Contiene únicamente los campos que la historia de usuario
 * permite modificar.
 *
 * El código del curso no se modifica, ya que funciona como
 * identificador académico del curso.
 */
public class ModificarCursoRequestDTO {
    // Omitir este apartado conserva los requisitos existentes.
    @jakarta.validation.Valid
    private ConfiguracionRequisitosCursoRequest requisitos;
    public ConfiguracionRequisitosCursoRequest getRequisitos() { return requisitos; }
    public void setRequisitos(ConfiguracionRequisitosCursoRequest value) { requisitos = value; }


    @NotBlank(message = "El nombre del curso es obligatorio.")
    @Size(
            max = 160,
            message = "El nombre del curso no puede superar los 160 caracteres."
    )
    private String nombre;

    @Size(max = 2000, message = "La descripción no puede superar los 2000 caracteres.")
    private String descripcion;

    @NotNull(message = "La cantidad de horas del curso es obligatoria.")
    @Positive(message = "La cantidad de horas debe ser mayor que cero.")
    @Max(
            value = 65535,
            message = "La cantidad de horas supera el máximo permitido."
    )
    private Integer horasTotales;

    @NotNull(message = "El programa académico es obligatorio.")
    @Positive(message = "El programa académico seleccionado no es válido.")
    private Long idPrograma;

    @NotNull(message = "El nivel académico es obligatorio.")
    @Positive(message = "El nivel académico debe ser mayor que cero.")
    @Max(value = 999, message = "El nivel académico no puede ser mayor que 999.")
    private Short nivel;


    // Se cursa en espacios de instrumento. Omitido = se conserva el valor actual.
    private Boolean esCursoInstrumento;

    /*
     * D7: edades opcionales (null = sin límite). Si la solicitud no trae
     * ninguno de los dos campos, se conservan los valores actuales
     * (edadesEnviadas queda en false).
     */
    @jakarta.validation.constraints.Min(value = 0, message = "La edad mínima no es válida.")
    @Max(value = 120, message = "La edad mínima no es válida.")
    private Integer edadMinima;

    @jakarta.validation.constraints.Min(value = 0, message = "La edad máxima no es válida.")
    @Max(value = 120, message = "La edad máxima no es válida.")
    private Integer edadMaxima;

    private boolean edadesEnviadas;

    public Integer getEdadMinima() {
        return edadMinima;
    }

    public void setEdadMinima(Integer edadMinima) {
        this.edadMinima = edadMinima;
        this.edadesEnviadas = true;
    }

    public Integer getEdadMaxima() {
        return edadMaxima;
    }

    public void setEdadMaxima(Integer edadMaxima) {
        this.edadMaxima = edadMaxima;
        this.edadesEnviadas = true;
    }

    public boolean isEdadesEnviadas() {
        return edadesEnviadas;
    }

    public Boolean getEsCursoInstrumento() {
        return esCursoInstrumento;
    }

    public void setEsCursoInstrumento(Boolean esCursoInstrumento) {
        this.esCursoInstrumento = esCursoInstrumento;
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