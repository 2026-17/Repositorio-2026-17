package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "documentos_estudiantes")
public class DocumentosEstudiante {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_documento", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_estudiante", nullable = false)
    private Estudiante idEstudiante;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_documento", nullable = false)
    private TiposDocumento idTipoDocumento;

    @Size(max = 255)
    @NotNull
    @Column(name = "nombre_original", nullable = false)
    private String nombreOriginal;

    @Size(max = 255)
    @NotNull
    @Column(name = "nombre_almacenado", nullable = false)
    private String nombreAlmacenado;

    @Size(max = 700)
    @NotNull
    @Column(name = "ubicacion_archivo", nullable = false, length = 700)
    private String ubicacionArchivo;

    @Size(max = 120)
    @NotNull
    @Column(name = "tipo_mime", nullable = false, length = 120)
    private String tipoMime;

    @NotNull
    @Column(name = "tamano_bytes", nullable = false)
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
    private Boolean esActual = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "id_usuario_carga")
    private Usuario idUsuarioCarga;

    @NotNull
    @CreationTimestamp
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "cargado_en", nullable = false)
    private Instant cargadoEn;

    @OneToMany
    private Set<AutorizacionesMatricula> autorizacionesMatriculas = new LinkedHashSet<>();

    @OneToMany
    private Set<MensualidadesEstudiante> mensualidadesEstudiantes = new LinkedHashSet<>();

    @PrePersist
    protected void alPersistir() {
        if (cargadoEn == null) cargadoEn = Instant.now();
    }

    // Bridge getters/setters for compatibility with service
    public void setEstudiante(Estudiante estudiante) { this.idEstudiante = estudiante; }
    public Estudiante getEstudiante() { return this.idEstudiante; }

}