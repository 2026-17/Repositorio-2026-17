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
@Table(name = "estudiantes")
public class Estudiante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estudiante", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario idUsuario;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_identificacion", nullable = false)
    private TipoIdentificacion idTipoIdentificacion;

    @NotNull
    @Size(max = 40)
    @Column(name = "numero_identificacion", nullable = false, length = 40)
    private String numeroIdentificacion;

    @NotNull
    @Size(max = 100)
    @Column(name = "nombres", nullable = false, length = 100)
    private String nombres;

    @NotNull
    @Size(max = 80)
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

    /*
     * No existe todavía una entidad CentroEducativo en el backend.
     * Por eso se conserva únicamente el identificador de la FK.
     */
    @Column(name = "id_centro_educativo")
    private Long idCentroEducativo;

    @Column(name = "condiciones_relevantes", columnDefinition = "TEXT")
    private String condicionesRelevantes;

    @NotNull
    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    @Column(name = "fecha_egreso")
    private LocalDate fechaEgreso;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_ultima_modificacion")
    private Usuario idUsuarioUltimaModificacion;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;
}