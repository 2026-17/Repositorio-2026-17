package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Catálogo de provincias de Costa Rica (solo lectura, ya viene cargado en la BD).
@Getter
@Setter
@Entity
@Table(name = "provincia")
public class Provincia {
    @Id
    @Column(name = "id", columnDefinition = "tinyint not null")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 20)
    private String nombre;
}
