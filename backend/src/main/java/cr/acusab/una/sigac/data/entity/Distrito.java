package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Catálogo de distritos; cada uno pertenece a un cantón. Al guardar el
// distrito de una persona, el cantón y la provincia se deducen de aquí.
@Getter
@Setter
@Entity
@Table(name = "distrito")
public class Distrito {
    @Id
    @Column(name = "codigo", nullable = false)
    private Integer codigo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "canton_codigo", nullable = false)
    private Canton canton;

    @Column(name = "nombre", nullable = false, length = 60)
    private String nombre;
}
