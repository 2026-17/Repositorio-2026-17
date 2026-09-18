package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(
        name = "notificaciones_estudiantes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_notificaciones_autorizacion",
                        columnNames = "id_autorizacion"
                )
        }
)
public class NotificacionEstudiante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(
            name = "id_notificacion",
            columnDefinition = "bigint UNSIGNED not null"
    )
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_estudiante",
            nullable = false
    )
    private Estudiante idEstudiante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_autorizacion")
    private AutorizacionesMatricula idAutorizacion;

    @NotNull
    @Size(max = 40)
    @Column(
            name = "tipo",
            nullable = false,
            length = 40
    )
    private String tipo;

    @NotNull
    @Size(max = 160)
    @Column(
            name = "asunto",
            nullable = false,
            length = 160
    )
    private String asunto;

    @NotNull
    @Size(max = 1500)
    @Column(
            name = "mensaje",
            nullable = false,
            length = 1500
    )
    private String mensaje;

    @NotNull
    @ColumnDefault("0")
    @Column(
            name = "leida",
            nullable = false
    )
    private Boolean leida = false;

    @NotNull
    @CreationTimestamp
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(
            name = "fecha_notificacion",
            nullable = false
    )
    private Instant fechaNotificacion;

    @Column(name = "fecha_lectura")
    private Instant fechaLectura;

    @PrePersist
    protected void alPersistir() {

        if (leida == null) {
            leida = false;
        }

        if (fechaNotificacion == null) {
            fechaNotificacion = Instant.now();
        }
    }
}