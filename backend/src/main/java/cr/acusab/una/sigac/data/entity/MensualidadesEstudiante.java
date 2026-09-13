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
@Table(name = "mensualidades_estudiantes")
public class MensualidadesEstudiante {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mensualidad", nullable = false)
    private Long id;

    @Column(name = "anio", columnDefinition = "smallint UNSIGNED not null")
    private Integer anio;

    @Column(name = "mes", columnDefinition = "tinyint UNSIGNED not null")
    private Short mes;

    @ManyToOne(fetch = FetchType.LAZY)
    private DocumentosEstudiante documentosEstudiantes;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "pagada", nullable = false)
    private Boolean pagada = false;

    @Column(name = "fecha_confirmacion")
    private Instant fechaConfirmacion;

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
        if (creadoEn == null) creadoEn = ahora;
        if (actualizadoEn == null) actualizadoEn = ahora;
    }

    @PreUpdate
    protected void alActualizar() {
        actualizadoEn = Instant.now();
    }
}