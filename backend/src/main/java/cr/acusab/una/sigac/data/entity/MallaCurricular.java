package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "malla_curricular")
public class MallaCurricular {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_malla", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @NotNull
    @Column(name = "id_plan_estudio", nullable = false)
    private Long idPlanEstudio;

    @NotNull
    @Column(name = "id_curso", nullable = false)
    private Long idCurso;

    @NotNull
    @Column(name = "nivel", nullable = false)
    private Short nivel;

    @NotNull
    @Column(name = "orden_en_nivel", nullable = false)
    private Short ordenEnNivel;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "obligatorio", nullable = false)
    private Boolean obligatorio;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "activo", nullable = false)
    private Boolean activo;

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