package cr.acusab.una.sigac.presentation.dto.response;

public class CursoResponseDTO {

    private Long idCurso;
    private String codigo;
    private String nombre;
    private String descripcion;
    private Integer horasTotales;
    private Boolean activo;

    private Long idMalla;
    private Short nivel;

    private Long idGrupo;
    private String codigoGrupo;
    private Short cupos;

    // getters y setters
    public Long getIdCurso() { return idCurso; }
    public void setIdCurso(Long idCurso) { this.idCurso = idCurso; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Integer getHorasTotales() { return horasTotales; }
    public void setHorasTotales(Integer horasTotales) { this.horasTotales = horasTotales; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public Long getIdMalla() { return idMalla; }
    public void setIdMalla(Long idMalla) { this.idMalla = idMalla; }
    public Short getNivel() { return nivel; }
    public void setNivel(Short nivel) { this.nivel = nivel; }
    public Long getIdGrupo() { return idGrupo; }
    public void setIdGrupo(Long idGrupo) { this.idGrupo = idGrupo; }
    public String getCodigoGrupo() { return codigoGrupo; }
    public void setCodigoGrupo(String codigoGrupo) { this.codigoGrupo = codigoGrupo; }
    public Short getCupos() { return cupos; }
    public void setCupos(Short cupos) { this.cupos = cupos; }
}