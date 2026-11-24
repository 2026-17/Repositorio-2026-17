package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.io.Serializable;
import java.time.Instant;

/*
 * Profesor de un grupo de agrupación. Un grupo puede tener varios
 * profesores y uno solo es el responsable (lo valida AgrupacionService).
 * grupos.id_profesor siempre es igual al responsable, para que las
 * pantallas que solo conocen un profesor sigan funcionando.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "grupos_profesores")
@IdClass(GrupoProfesor.Clave.class)
public class GrupoProfesor {

    @Id
    @Column(name = "id_grupo", columnDefinition = "int UNSIGNED not null")
    private Long idGrupo;

    @Id
    @Column(name = "id_profesor", columnDefinition = "int UNSIGNED not null")
    private Long idProfesor;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "es_responsable", nullable = false)
    private Boolean esResponsable = false;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    public GrupoProfesor(Long idGrupo, Long idProfesor, boolean esResponsable) {
        this.idGrupo = idGrupo;
        this.idProfesor = idProfesor;
        this.esResponsable = esResponsable;
    }

    @PrePersist
    protected void alPersistir() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Clave implements Serializable {
        private Long idGrupo;
        private Long idProfesor;
    }
}
