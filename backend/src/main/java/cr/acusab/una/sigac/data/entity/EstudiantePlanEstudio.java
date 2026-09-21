package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.time.LocalDate;

/*
 * Inscripción de un estudiante en un plan de estudio.
 *
 * Un estudiante puede estar inscrito en varios planes. Solo puede
 * matricular cursos de los planes en los que está inscrito y activo.
 */
@Getter
@Setter
@Entity
@Table(
        name = "estudiantes_planes_estudio",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_estudiantes_planes_estudio",
                        columnNames = {"id_estudiante", "id_plan_estudio"}
                )
        }
)
public class EstudiantePlanEstudio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estudiante_plan", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @NotNull
    @Column(name = "id_estudiante", nullable = false)
    private Long idEstudiante;

    @NotNull
    @Column(name = "id_plan_estudio", nullable = false)
    private Long idPlanEstudio;

    @NotNull
    @Column(name = "fecha_inscripcion", nullable = false)
    private LocalDate fechaInscripcion;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

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

    @PrePersist
    protected void alPersistir() {

        Instant ahora = Instant.now();

        if (fechaInscripcion == null) {
            fechaInscripcion = LocalDate.now();
        }

        creadoEn = ahora;
        actualizadoEn = ahora;
    }

    @PreUpdate
    protected void alActualizar() {
        actualizadoEn = Instant.now();
    }
}
