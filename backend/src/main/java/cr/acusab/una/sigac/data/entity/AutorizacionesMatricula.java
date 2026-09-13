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
@Table(name = "autorizaciones_matricula")
public class AutorizacionesMatricula {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_autorizacion", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private DocumentosEstudiante documentosEstudiantes;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "mensualidades_al_dia", nullable = false)
    private Boolean mensualidadesAlDia = false;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "pago_matricula_confirmado", nullable = false)
    private Boolean pagoMatriculaConfirmado = false;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "documentacion_completa", nullable = false)
    private Boolean documentacionCompleta = false;

    @Column(name = "fecha_revision")
    private Instant fechaRevision;

    @Size(max = 1500)
    @Column(name = "observaciones", length = 1500)
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
        if (creadoEn == null) creadoEn = ahora;
        if (actualizadoEn == null) actualizadoEn = ahora;
    }

    @PreUpdate
    protected void alActualizar() {
        actualizadoEn = Instant.now();
    }
}