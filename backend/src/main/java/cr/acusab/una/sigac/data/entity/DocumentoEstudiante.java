package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "documentos_estudiantes")
public class DocumentoEstudiante {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_documento", columnDefinition = "bigint UNSIGNED not null")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estudiante", nullable = false)
    private Estudiante idEstudiante;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_documento", nullable = false)
    private TipoDocumento idTipoDocumento;

    @NotNull
    @Size(max = 255)
    @Column(name = "nombre_original", nullable = false, length = 255)
    private String nombreOriginal;

    @NotNull
    @Size(max = 255)
    @Column(name = "nombre_almacenado", nullable = false, length = 255)
    private String nombreAlmacenado;

    @NotNull
    @Size(max = 700)
    @Column(name = "ubicacion_archivo", nullable = false, length = 700)
    private String ubicacionArchivo;

    @NotNull
    @Size(max = 120)
    @Column(name = "tipo_mime", nullable = false, length = 120)
    private String tipoMime;

    @NotNull
    @Column(name = "tamano_bytes", nullable = false, columnDefinition = "bigint UNSIGNED not null")
    private Long tamanoBytes;

    @Size(max = 64)
    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "fecha_emision")
    private LocalDate fechaEmision;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "es_actual", nullable = false)
    private Boolean esActual;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_carga")
    private Usuario idUsuarioCarga;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "cargado_en", nullable = false)
    private Instant cargadoEn;
}
