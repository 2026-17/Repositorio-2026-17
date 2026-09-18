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
@Table(name = "estados_autorizaciones_matricula")
public class EstadosAutorizacionesMatricula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(
            name = "id_estado_autorizacion",
            columnDefinition = "smallint UNSIGNED not null"
    )
    private Integer id;

    @NotNull
    @Size(max = 30)
    @Column(
            name = "codigo",
            nullable = false,
            length = 30
    )
    private String codigo;

    @NotNull
    @Size(max = 80)
    @Column(
            name = "nombre",
            nullable = false,
            length = 80
    )
    private String nombre;

    @Size(max = 255)
    @Column(
            name = "descripcion",
            length = 255
    )
    private String descripcion;

    @NotNull
    @ColumnDefault("1")
    @Column(
            name = "activo",
            nullable = false
    )
    private Boolean activo = true;
}