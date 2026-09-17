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
@Table(
        name = "autorizaciones_matricula",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_autorizaciones_estudiante_periodo",
                        columnNames = {"id_estudiante", "id_periodo"}
                )
        }
)
public class AutorizacionesMatricula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(
            name = "id_autorizacion",
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

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_periodo",
            nullable = false
    )
    private PeriodoAcademico idPeriodo;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "id_estado_autorizacion",
            nullable = false
    )
    private EstadosAutorizacionesMatricula idEstadoAutorizacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "id_documento_comprobante_matricula"
    )
    private DocumentosEstudiante idDocumentoComprobanteMatricula;

    @NotNull
    @ColumnDefault("0")
    @Column(
            name = "mensualidades_al_dia",
            nullable = false
    )
    private Boolean mensualidadesAlDia = false;

    @NotNull
    @ColumnDefault("0")
    @Column(
            name = "pago_matricula_confirmado",
            nullable = false
    )
    private Boolean pagoMatriculaConfirmado = false;

    @NotNull
    @ColumnDefault("0")
    @Column(
            name = "documentacion_completa",
            nullable = false
    )
    private Boolean documentacionCompleta = false;

    @NotNull
    @ColumnDefault("0")
    @Column(
            name = "estudiante_activo",
            nullable = false
    )
    private Boolean estudianteActivo = false;

    @NotNull
    @ColumnDefault("0")
    @Column(
            name = "datos_completos",
            nullable = false
    )
    private Boolean datosCompletos = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "id_usuario_revisa"
    )
    private Usuario idUsuarioRevisa;

    @Column(name = "fecha_revision")
    private Instant fechaRevision;

    @Size(max = 1500)
    @Column(
            name = "observaciones",
            length = 1500
    )
    private String observaciones;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(
            name = "creado_en",
            nullable = false
    )
    private Instant creadoEn;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(
            name = "actualizado_en",
            nullable = false
    )
    private Instant actualizadoEn;

    @PrePersist
    protected void alPersistir() {

        Instant ahora = Instant.now();

        if (mensualidadesAlDia == null) {
            mensualidadesAlDia = false;
        }

        if (pagoMatriculaConfirmado == null) {
            pagoMatriculaConfirmado = false;
        }

        if (documentacionCompleta == null) {
            documentacionCompleta = false;
        }

        if (estudianteActivo == null) {
            estudianteActivo = false;
        }

        if (datosCompletos == null) {
            datosCompletos = false;
        }

        if (creadoEn == null) {
            creadoEn = ahora;
        }

        if (actualizadoEn == null) {
            actualizadoEn = ahora;
        }
    }

    @PreUpdate
    protected void alActualizar() {
        actualizadoEn = Instant.now();
    }
}