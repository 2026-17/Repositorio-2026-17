package cr.acusab.una.sigac.presentation.dto.response;

import java.util.ArrayList;
import java.util.List;

public class RequisitosCursoResponseDTO {

    private Long idPlanEstudio;
    private Long idCurso;
    private String codigoCurso;
    private String nombreCurso;
    private Integer edadMinima;
    private Integer edadMaxima;
    private List<CursoRequisitoDTO> cursos = new ArrayList<>();
    private List<Long> prerrequisitos = new ArrayList<>();
    private List<Long> correquisitos = new ArrayList<>();

    public Long getIdPlanEstudio() { return idPlanEstudio; }
    public void setIdPlanEstudio(Long idPlanEstudio) { this.idPlanEstudio = idPlanEstudio; }
    public Long getIdCurso() { return idCurso; }
    public void setIdCurso(Long idCurso) { this.idCurso = idCurso; }
    public String getCodigoCurso() { return codigoCurso; }
    public void setCodigoCurso(String codigoCurso) { this.codigoCurso = codigoCurso; }
    public String getNombreCurso() { return nombreCurso; }
    public void setNombreCurso(String nombreCurso) { this.nombreCurso = nombreCurso; }
    public Integer getEdadMinima() { return edadMinima; }
    public void setEdadMinima(Integer edadMinima) { this.edadMinima = edadMinima; }
    public Integer getEdadMaxima() { return edadMaxima; }
    public void setEdadMaxima(Integer edadMaxima) { this.edadMaxima = edadMaxima; }
    public List<CursoRequisitoDTO> getCursos() { return cursos; }
    public void setCursos(List<CursoRequisitoDTO> cursos) { this.cursos = cursos; }
    public List<Long> getPrerrequisitos() { return prerrequisitos; }
    public void setPrerrequisitos(List<Long> prerrequisitos) { this.prerrequisitos = prerrequisitos; }
    public List<Long> getCorrequisitos() { return correquisitos; }
    public void setCorrequisitos(List<Long> correquisitos) { this.correquisitos = correquisitos; }

    public static class CursoRequisitoDTO {
        private Long idCurso;
        private String codigo;
        private String nombre;
        private Short nivel;
        private Short ordenEnNivel;

        public CursoRequisitoDTO() { }

        public CursoRequisitoDTO(Long idCurso, String codigo, String nombre, Short nivel, Short ordenEnNivel) {
            this.idCurso = idCurso;
            this.codigo = codigo;
            this.nombre = nombre;
            this.nivel = nivel;
            this.ordenEnNivel = ordenEnNivel;
        }

        public Long getIdCurso() { return idCurso; }
        public String getCodigo() { return codigo; }
        public String getNombre() { return nombre; }
        public Short getNivel() { return nivel; }
        public Short getOrdenEnNivel() { return ordenEnNivel; }
    }

    public static class PlanDTO {
        private Long idPlanEstudio;
        private String codigo;
        private String nombre;
        private String version;

        public PlanDTO() { }

        public PlanDTO(Long idPlanEstudio, String codigo, String nombre, String version) {
            this.idPlanEstudio = idPlanEstudio;
            this.codigo = codigo;
            this.nombre = nombre;
            this.version = version;
        }

        public Long getIdPlanEstudio() { return idPlanEstudio; }
        public String getCodigo() { return codigo; }
        public String getNombre() { return nombre; }
        public String getVersion() { return version; }
    }
}
