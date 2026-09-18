package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "estudiantes_encargados")
@IdClass(EstudianteEncargadoId.class)
public class EstudianteEncargado {

    @Id
    @Column(name = "id_estudiante", columnDefinition = "int UNSIGNED not null")
    private Long idEstudiante;

    @Id
    @Column(name = "id_encargado", columnDefinition = "int UNSIGNED not null")
    private Long idEncargado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_estudiante",
            insertable = false,
            updatable = false,
            nullable = false
    )
    private Estudiante estudiante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_encargado",
            insertable = false,
            updatable = false,
            nullable = false
    )
    private Encargado encargado;

    @NotNull
    @Size(max = 30)
    @Column(name = "parentesco", nullable = false, length = 30)
    private String parentesco;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "es_responsable_principal", nullable = false)
    private Boolean esResponsablePrincipal;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "es_contacto_emergencia", nullable = false)
    private Boolean esContactoEmergencia;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "autorizado_retiro", nullable = false)
    private Boolean autorizadoRetiro;

    @Size(max = 255)
    @Column(name = "observaciones", length = 255)
    private String observaciones;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
}