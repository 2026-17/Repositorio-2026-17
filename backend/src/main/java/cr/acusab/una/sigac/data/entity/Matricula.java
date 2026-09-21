package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

/*
 * Matrícula de un estudiante en un período.
 *
 * Existe una sola por autorización (uq_matriculas_autorizacion): las
 * confirmaciones posteriores del mismo período agregan grupos a ella.
 */
@Getter
@Setter
@Entity
@Table(name = "matriculas")
public class Matricula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_matricula", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_autorizacion", nullable = false)
    private AutorizacionesMatricula autorizacion;

    @NotNull
    @Column(name = "id_plan_estudio", nullable = false)
    private Long idPlanEstudio;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estado_matricula", nullable = false)
    private EstadoMatricula estado;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "fecha_matricula", nullable = false)
    private Instant fechaMatricula;

    @Size(max = 1000)
    @Column(name = "observaciones", length = 1000)
    private String observaciones;

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

        if (fechaMatricula == null) {
            fechaMatricula = ahora;
        }

        creadoEn = ahora;
        actualizadoEn = ahora;
    }

    @PreUpdate
    protected void alActualizar() {
        actualizadoEn = Instant.now();
    }
}
