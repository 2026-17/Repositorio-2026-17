package cr.acusab.una.sigac.presentation.dto.response;

/*
 * HU-030 - Modificar curso de la malla curricular.
 *
 * DTO utilizado para:
 *
 * 1. Consultar la información actual de un curso.
 * 2. Precargar el formulario de modificación.
 * 3. Retornar el curso una vez actualizado.
 */
public class CursoEdicionResponseDTO {

    private Long idCurso;
    private String codigo;
    private String nombre;
    private String descripcion;
    private Integer horasTotales;
    private Boolean activo;

    private Long idPrograma;
    private String programa;

    private Long idPlanEstudio;
    private Short nivel;

    public Long getIdCurso() {
        return idCurso;
    }

    public void setIdCurso(Long idCurso) {
        this.idCurso = idCurso;
    }

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

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public Long getIdPrograma() {
        return idPrograma;
    }

    public void setIdPrograma(Long idPrograma) {
        this.idPrograma = idPrograma;
    }

    public String getPrograma() {
        return programa;
    }

    public void setPrograma(String programa) {
        this.programa = programa;
    }

    public Long getIdPlanEstudio() {
        return idPlanEstudio;
    }

    public void setIdPlanEstudio(Long idPlanEstudio) {
        this.idPlanEstudio = idPlanEstudio;
    }

    public Short getNivel() {
        return nivel;
    }

    public void setNivel(Short nivel) {
        this.nivel = nivel;
    }
}