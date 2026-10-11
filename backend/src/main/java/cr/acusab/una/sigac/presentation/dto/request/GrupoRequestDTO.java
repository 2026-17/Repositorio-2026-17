package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/*
 * Creación de un grupo (oferta concreta de un curso en un período).
 */
public class GrupoRequestDTO {

    // --- Curso y período ---

    @NotNull(message = "El curso es obligatorio.")
    @Positive(message = "El curso seleccionado no es válido.")
    private Long idCurso;

    @NotNull(message = "El período académico es obligatorio.")
    @Positive(message = "El período académico seleccionado no es válido.")
    private Long idPeriodo;

    // --- Datos del grupo ---

    @NotBlank(message = "El código del grupo es obligatorio.")
    @Size(max = 40, message = "El código del grupo no puede superar los 40 caracteres.")
    private String codigo;

    @NotNull(message = "El cupo máximo es obligatorio.")
    @Positive(message = "El cupo máximo debe ser mayor que cero.")
    @Max(value = 32767, message = "El cupo máximo supera el valor permitido.")
    private Short cupoMaximo;

    // Opcional: el grupo puede crearse sin docente asignado.
    @Positive(message = "El profesor seleccionado no es válido.")
    private Long idProfesor;

    @NotBlank(message = "La modalidad es obligatoria.")
    private String modalidad;

    @Size(max = 80, message = "El aula no puede superar los 80 caracteres.")
    private String aula;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    @NotBlank(message = "El estado del grupo es obligatorio.")
    private String estado;

    // --- Horario semanal ---

    @NotEmpty(message = "Agregue al menos un horario para el grupo.")
    private List<@Valid @NotNull HorarioGrupoRequestDTO> horarios = new ArrayList<>();

    // --- Getters y setters ---

    public Long getIdCurso() {
        return idCurso;
    }

    public void setIdCurso(Long idCurso) {
        this.idCurso = idCurso;
    }

    public Long getIdPeriodo() {
        return idPeriodo;
    }

    public void setIdPeriodo(Long idPeriodo) {
        this.idPeriodo = idPeriodo;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public Short getCupoMaximo() {
        return cupoMaximo;
    }

    public void setCupoMaximo(Short cupoMaximo) {
        this.cupoMaximo = cupoMaximo;
    }

    public Long getIdProfesor() {
        return idProfesor;
    }

    public void setIdProfesor(Long idProfesor) {
        this.idProfesor = idProfesor;
    }

    public String getModalidad() {
        return modalidad;
    }

    public void setModalidad(String modalidad) {
        this.modalidad = modalidad;
    }

    public String getAula() {
        return aula;
    }

    public void setAula(String aula) {
        this.aula = aula;
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

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public List<HorarioGrupoRequestDTO> getHorarios() {
        return horarios;
    }

    public void setHorarios(List<HorarioGrupoRequestDTO> horarios) {
        this.horarios = horarios;
    }
}
