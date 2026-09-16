package cr.acusab.una.sigac.presentation.dto.response;

import java.util.List;

public class RequisitosCursoResponseDTO {
    private Long idCurso;
    private Long idPlanEstudio;
    private List<Long> prerrequisitos;
    private List<Long> correquisitos;
    private String mensaje;

    public RequisitosCursoResponseDTO() {}

    public RequisitosCursoResponseDTO(Long idCurso, Long idPlanEstudio,
                                      List<Long> prerrequisitos,
                                      List<Long> correquisitos,
                                      String mensaje) {
        this.idCurso = idCurso;
        this.idPlanEstudio = idPlanEstudio;
        this.prerrequisitos = prerrequisitos;
        this.correquisitos = correquisitos;
        this.mensaje = mensaje;
    }

    public Long getIdCurso() { return idCurso; }
    public void setIdCurso(Long idCurso) { this.idCurso = idCurso; }
    public Long getIdPlanEstudio() { return idPlanEstudio; }
    public void setIdPlanEstudio(Long idPlanEstudio) { this.idPlanEstudio = idPlanEstudio; }
    public List<Long> getPrerrequisitos() { return prerrequisitos; }
    public void setPrerrequisitos(List<Long> prerrequisitos) { this.prerrequisitos = prerrequisitos; }
    public List<Long> getCorrequisitos() { return correquisitos; }
    public void setCorrequisitos(List<Long> correquisitos) { this.correquisitos = correquisitos; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
}
