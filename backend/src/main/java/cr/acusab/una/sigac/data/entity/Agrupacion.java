package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

/*
 * Agrupación del catálogo: Orquesta, Banda, Coro de niños...
 *
 * Es un grupo con horario fijo donde tocan juntos estudiantes de distintos
 * niveles. Cada período se abre como un grupo de tipo AGRUPACION
 * (grupos.id_agrupacion). El tipo decide qué cursos cuenta:
 * - INSTRUMENTAL: Conjunto Instrumental I a XII.
 * - CORAL: Coro I a XIV.
 * Los instrumentos permitidos están en agrupaciones_instrumentos (sin
 * filas = cualquier instrumento).
 */
@Getter
@Setter
@Entity
@Table(name = "agrupaciones")
public class Agrupacion {

    public static final String TIPO_INSTRUMENTAL = "INSTRUMENTAL";
    public static final String TIPO_CORAL = "CORAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_agrupacion", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @NotNull
    @Size(max = 20)
    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @NotNull
    @Size(max = 100)
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @NotNull
    @Size(max = 20)
    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    @Size(max = 500)
    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "activa", nullable = false)
    private Boolean activa = true;

    // D6: solo la matriculan estudiantes de tipo INCLUSION.
    @NotNull
    @ColumnDefault("0")
    @Column(name = "solo_inclusion", nullable = false)
    private Boolean soloInclusion = false;

    // D7: años cumplidos a la fecha de inicio del período (NULL = sin límite).
    @Column(name = "edad_minima", columnDefinition = "tinyint UNSIGNED")
    private Integer edadMinima;

    @Column(name = "edad_maxima", columnDefinition = "tinyint UNSIGNED")
    private Integer edadMaxima;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "id_usuario_ultima_modificacion")
    private Long idUsuarioUltimaModificacion;

    public boolean esInstrumental() {
        return TIPO_INSTRUMENTAL.equals(tipo);
    }

    public boolean esCoral() {
        return TIPO_CORAL.equals(tipo);
    }

    @PrePersist
    protected void alPersistir() {
        Instant ahora = Instant.now();
        if (creadoEn == null) {
            creadoEn = ahora;
        }
        actualizadoEn = ahora;
    }

    @PreUpdate
    protected void alActualizar() {
        actualizadoEn = Instant.now();
    }
}
