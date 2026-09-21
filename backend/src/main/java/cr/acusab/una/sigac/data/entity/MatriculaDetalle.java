package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.time.LocalDate;

/*
 * Grupo incluido en una matrícula. Mientras "retirado" sea falso, el
 * grupo ocupa un cupo.
 */
@Getter
@Setter
@Entity
@Table(name = "matriculas_detalles")
public class MatriculaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_matricula", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_matricula", nullable = false)
    private Matricula matricula;

    @NotNull
    @Column(name = "id_grupo", nullable = false)
    private Long idGrupo;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "retirado", nullable = false)
    private Boolean retirado = false;

    @Column(name = "fecha_retiro")
    private LocalDate fechaRetiro;

    @Size(max = 500)
    @Column(name = "motivo_retiro", length = 500)
    private String motivoRetiro;

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

        creadoEn = ahora;
        actualizadoEn = ahora;
    }

    @PreUpdate
    protected void alActualizar() {
        actualizadoEn = Instant.now();
    }
}
