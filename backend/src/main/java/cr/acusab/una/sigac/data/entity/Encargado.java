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
@Table(name = "encargados")
public class Encargado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_encargado", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_identificacion")
    private TiposIdentificacion idTipoIdentificacion;

    @Size(max = 40)
    @Column(name = "numero_identificacion", length = 40)
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

    @Size(max = 30)
    @NotNull
    @Column(name = "telefono_principal", nullable = false, length = 30)
    private String telefonoPrincipal;

    @Size(max = 30)
    @Column(name = "telefono_secundario", length = 30)
    private String telefonoSecundario;

    @Size(max = 254)
    @Column(name = "correo", length = 254)
    private String correo;

    @Size(max = 120)
    @Column(name = "ocupacion", length = 120)
    private String ocupacion;

    @Size(max = 500)
    @Column(name = "direccion", length = 500)
    private String direccion;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "activo", nullable = false)
    private Boolean activo = false;

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