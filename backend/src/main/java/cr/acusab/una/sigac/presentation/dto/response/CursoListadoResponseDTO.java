package cr.acusab.una.sigac.presentation.dto.response;

/*
 * HU-031 - Desactivar curso.
 *
 * DTO utilizado por GET /cursos para alimentar el listado de
 * cursos activos que el Director Académico puede desactivar.
 *
 * categoria corresponde al campo descripcion de Curso, que en la
 * práctica se usa con valores fijos (Instrumento, Teoría,
 * Conjunto, Iniciación) para clasificar el curso.
 *
 * docente / cupoMaximo provienen del grupo más reciente asociado
 * al curso, ya que el docente y el cupo no son atributos del
 * curso en sí, sino de cada grupo (un curso puede tener varios
 * grupos en distintos periodos).
 */
public class CursoListadoResponseDTO {

    private Long idCurso;
    private String codigo;
    private String nombre;
    private String categoria;
    private Integer horasTotales;

    private String docente;
    private Short cupoMaximo;

    public Long getIdCurso() { return idCurso; }
    public void setIdCurso(Long idCurso) { this.idCurso = idCurso; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public Integer getHorasTotales() { return horasTotales; }
    public void setHorasTotales(Integer horasTotales) { this.horasTotales = horasTotales; }

    public String getDocente() { return docente; }
    public void setDocente(String docente) { this.docente = docente; }

    public Short getCupoMaximo() { return cupoMaximo; }
    public void setCupoMaximo(Short cupoMaximo) { this.cupoMaximo = cupoMaximo; }
}
