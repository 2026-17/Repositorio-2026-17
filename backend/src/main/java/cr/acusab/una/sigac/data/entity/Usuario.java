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
@Table(name = "usuarios")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @Size(max = 254)
    @NotNull
    @Column(name = "correo", nullable = false, length = 254)
    private String correo;

    @Size(max = 255)
    @NotNull
    @Column(name = "hash_contrasena", nullable = false)
    private String hashContrasena;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "requiere_cambio_contrasena", nullable = false)
    private Boolean requiereCambioContrasena;

    @ColumnDefault("'0'")
    @Column(name = "intentos_fallidos", columnDefinition = "tinyint UNSIGNED not null")
    private Short intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    @Column(name = "ultimo_acceso")
    private Instant ultimoAcceso;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;


}