package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class RequisitosCursoRequestDTO {

    @NotNull(message = "El identificador del curso es obligatorio.")
    @Positive(message = "El identificador del curso no es válido.")
    private Long idCurso;

    private List<@Positive(message = "El identificador de un prerrequisito no es válido.") Long> prerrequisitos;

    private List<@Positive(message = "El identificador de un correquisito no es válido.") Long> correquisitos;

    public Long getIdCurso() { return idCurso; }
    public void setIdCurso(Long idCurso) { this.idCurso = idCurso; }

    public List<Long> getPrerrequisitos() { return prerrequisitos; }
    public void setPrerrequisitos(List<Long> prerrequisitos) { this.prerrequisitos = prerrequisitos; }

    public List<Long> getCorrequisitos() { return correquisitos; }
    public void setCorrequisitos(List<Long> correquisitos) { this.correquisitos = correquisitos; }
}
