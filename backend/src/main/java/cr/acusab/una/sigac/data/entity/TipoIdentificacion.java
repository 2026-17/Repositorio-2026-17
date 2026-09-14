package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@Entity
@Table(name = "tipos_identificacion")
public class TipoIdentificacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_identificacion", columnDefinition = "smallint UNSIGNED not null")
    private Integer id;

    @Size(max = 20)
    @NotNull
    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @Size(max = 60)
    @NotNull
    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "activo", nullable = false)
    private Boolean activo;
}
