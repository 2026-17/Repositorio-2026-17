package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/*
 * Catálogo de tipos de estudiante (REGULAR, INCLUSION).
 *
 * Es una categoría administrativa: no se deduce de las condiciones
 * relevantes del estudiante ni guarda diagnósticos. Un estudiante de
 * inclusión tiene beca permanente del 100% (ver BecaService).
 *
 * La tabla se crea con db/cambios/2026-11-17_tipo_estudiante_inclusion.sql.
 */
@Getter
@Setter
@Entity
@Table(name = "tipos_estudiante")
public class TiposEstudiante {

    public static final String CODIGO_REGULAR = "REGULAR";
    public static final String CODIGO_INCLUSION = "INCLUSION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_estudiante", columnDefinition = "smallint UNSIGNED not null")
    private Integer id;

    @Size(max = 30)
    @NotNull
    @Column(name = "codigo", nullable = false, length = 30)
    private String codigo;

    @Size(max = 100)
    @NotNull
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Size(max = 500)
    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    public boolean esInclusion() {
        return CODIGO_INCLUSION.equalsIgnoreCase(codigo);
    }
}
