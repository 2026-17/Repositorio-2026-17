package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/*
 * Instrumento permitido en una agrupación. Si la agrupación no tiene
 * ninguno, acepta estudiantes de cualquier instrumento.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agrupaciones_instrumentos")
@IdClass(AgrupacionInstrumento.Clave.class)
public class AgrupacionInstrumento {

    @Id
    @Column(name = "id_agrupacion", columnDefinition = "int UNSIGNED not null")
    private Long idAgrupacion;

    @Id
    @Column(name = "id_instrumento", columnDefinition = "smallint UNSIGNED not null")
    private Integer idInstrumento;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long idAgrupacion;
        private Integer idInstrumento;
    }
}
