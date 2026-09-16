package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "requisitos_cursos")
public class RequisitoCurso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_requisito", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @NotNull
    @Column(name = "id_plan_estudio", nullable = false)
    private Long idPlanEstudio;

    @NotNull
    @Column(name = "id_curso", nullable = false)
    private Long idCurso;

    @NotNull
    @Column(name = "id_curso_requerido", nullable = false)
    private Long idCursoRequerido;

    @NotNull
    @Column(name = "id_tipo_requisito", nullable = false)
    private Short idTipoRequisito;

    @Column(name = "nota_minima", precision = 5, scale = 2)
    private BigDecimal notaMinima;

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
