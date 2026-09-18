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
@Table(name = "profesores")
public class Profesor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_profesor", columnDefinition = "int UNSIGNED not null")
    private Long id;

    // Cuenta de usuario asociada al profesor (opcional; se crea aparte con HU-034)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private Usuario idUsuario;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_identificacion", nullable = false)

    private TiposIdentificacion idTipoIdentificacion;

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

    @Size(max = 30)
    @Column(name = "telefono", length = 30)
    private String telefono;

    @Size(max = 254)
    @Column(name = "correo", length = 254)
    private String correo;

    @Size(max = 500)
    @Column(name = "direccion", length = 500)
    private String direccion;

    @Size(max = 120)
    @Column(name = "grado_academico", length = 120)
    private String gradoAcademico;

    @Size(max = 180)
    @Column(name = "especialidad", length = 180)
    private String especialidad;

    @Size(max = 180)
    @Column(name = "empleador", length = 180)
    private String empleador;

    @NotNull
    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    @Column(name = "fecha_salida")
    private LocalDate fechaSalida;

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
