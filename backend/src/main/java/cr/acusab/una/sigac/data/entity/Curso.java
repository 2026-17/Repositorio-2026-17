package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
        import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "cursos")
public class Curso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_curso", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @Size(max = 30)
    @NotNull
    @Column(name = "codigo", nullable = false, length = 30)
    private String codigo;

    @Size(max = 160)
    @NotNull
    @Column(name = "nombre", nullable = false, length = 160)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "horas_totales")
    private Integer horasTotales;

    @Column(name = "creditos", precision = 4, scale = 1)
    private BigDecimal creditos;

    @Min(0)
    @Max(150)
    @Column(name = "edad_minima")
    private Integer edadMinima;

    @Min(0)
    @Max(150)
    @Column(name = "edad_maxima")
    private Integer edadMaxima;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "activo", nullable = false)
    private Boolean activo;

    /*
     * Instrumento I a XIV: no se ofrecen en grupos normales, se cursan en
     * los espacios de instrumento de cada profesor.
     */
    @NotNull
    @ColumnDefault("0")
    @Column(name = "es_curso_instrumento", nullable = false)
    private Boolean esCursoInstrumento = false;

    /*
     * Conjunto Instrumental (CI-%) y Coro (CORO-%): se cursan en las
     * agrupaciones (el nivel lo calcula el sistema). No se ofrecen como
     * cursos normales ni se abren grupos de tipo CURSO para ellos.
     */
    @NotNull
    @ColumnDefault("0")
    @Column(name = "es_curso_agrupacion", nullable = false)
    private Boolean esCursoAgrupacion = false;

    @Column(name = "id_usuario_ultima_modificacion")
    private Long idUsuarioUltimaModificacion;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;
}