package cr.acusab.una.sigac.presentation.dto.response;

public class CursoResponseDTO {

    private Long id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private Integer horasTotales;
    private Boolean activo;

    // getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
}