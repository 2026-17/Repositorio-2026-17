package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Catálogo de cantones; cada uno pertenece a una provincia.
@Getter
@Setter
@Entity
@Table(name = "canton")
public class Canton {
    @Id
    @Column(name = "codigo", columnDefinition = "smallint not null")
    private Integer codigo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "provincia_id", nullable = false)
    private Provincia provincia;

    @Column(name = "nombre", nullable = false, length = 50)
    private String nombre;
}
