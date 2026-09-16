package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class RequisitosCursoRequestDTO {

    @NotNull(message = "El plan de estudio es obligatorio.")
    @Positive(message = "El plan de estudio no es válido.")
    private Long idPlanEstudio;

    @NotNull(message = "El curso es obligatorio.")
    @Positive(message = "El curso no es válido.")
    private Long idCurso;

    private List<Long> prerrequisitos;
    private List<Long> correquisitos;

    public Long getIdPlanEstudio() { return idPlanEstudio; }
    public void setIdPlanEstudio(Long idPlanEstudio) { this.idPlanEstudio = idPlanEstudio; }

    public Long getIdCurso() { return idCurso; }
    public void setIdCurso(Long idCurso) { this.idCurso = idCurso; }

    public List<Long> getPrerrequisitos() { return prerrequisitos; }
    public void setPrerrequisitos(List<Long> prerrequisitos) { this.prerrequisitos = prerrequisitos; }

    public List<Long> getCorrequisitos() { return correquisitos; }
    public void setCorrequisitos(List<Long> correquisitos) { this.correquisitos = correquisitos; }
}
