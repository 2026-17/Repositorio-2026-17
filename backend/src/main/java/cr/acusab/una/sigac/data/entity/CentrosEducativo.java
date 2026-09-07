package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "centros_educativos")
public class CentrosEducativo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_centro_educativo", columnDefinition = "int UNSIGNED not null")
    private Long id;

    @Size(max = 30)
    @Column(name = "codigo", length = 30)
    private String codigo;

    @Size(max = 180)
    @NotNull
    @Column(name = "nombre", nullable = false, length = 180)
    private String nombre;

    @Size(max = 50)
    @Column(name = "tipo", length = 50)
    private String tipo;

    @Size(max = 80)
    @Column(name = "provincia", length = 80)
    private String provincia;

    @Size(max = 80)
    @Column(name = "canton", length = 80)
    private String canton;

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

    @OneToMany
    @JoinColumn(name = "id_centro_educativo")
    private Set<Estudiante> estudiantes = new LinkedHashSet<>();

}