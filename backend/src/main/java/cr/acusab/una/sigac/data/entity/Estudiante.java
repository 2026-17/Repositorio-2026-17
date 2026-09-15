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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "estudiantes")
public class Estudiante {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estudiante", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_identificacion", nullable = false)
    private TiposIdentificacion idTipoIdentificacion;

    @Size(max = 40)
    @NotNull
    @Column(name = "numero_identificacion", nullable = false, length = 40)
    private String numeroIdentificacion;

    @Size(max = 100)
    @NotNull
    @Column(name = "nombres", nullable = false, length = 100)
    private String nombres;

    @Size(max = 80)
    @NotNull
    @Column(name = "primer_apellido", nullable = false, length = 80)
    private String primerApellido;

    @Size(max = 80)
    @Column(name = "segundo_apellido", length = 80)
    private String segundoApellido;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Size(max = 30)
    @Column(name = "genero", length = 30)
    private String genero;

    @Size(max = 30)
    @Column(name = "telefono", length = 30)
    private String telefono;

    @Size(max = 254)
    @Column(name = "correo", length = 254)
    private String correo;

    @Size(max = 500)
    @Column(name = "direccion", length = 500)
    private String direccion;

    @Size(max = 80)
    @Column(name = "nivel_escolar", length = 80)
    private String nivelEscolar;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "id_centro_educativo")
    private CentrosEducativo idCentroEducativo;

    @Lob
    @Column(name = "condiciones_relevantes")
    private String condicionesRelevantes;

    @NotNull
    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    @Column(name = "fecha_egreso")
    private LocalDate fechaEgreso;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "activo", nullable = false)
    private Boolean activo = false;

    @NotNull
    @CreationTimestamp
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @NotNull
    @UpdateTimestamp
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Size(max = 700)
    @Column(name = "foto_perfil_ruta", length = 700)
    private String fotoPerfilRuta;

    @Size(max = 255)
    @Column(name = "foto_perfil_nombre")
    private String fotoPerfilNombre;

    @Size(max = 50)
    @Column(name = "foto_perfil_mime", length = 50)
    private String fotoPerfilMime;

    @Column(name = "foto_perfil_tamano_bytes", columnDefinition = "int UNSIGNED")
    private Long fotoPerfilTamanoBytes;

    @Column(name = "foto_perfil_actualizada_en")
    private Instant fotoPerfilActualizadaEn;

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