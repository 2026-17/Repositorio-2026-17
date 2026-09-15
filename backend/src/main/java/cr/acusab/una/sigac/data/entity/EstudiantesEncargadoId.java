package cr.acusab.una.sigac.data.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@Embeddable
public class EstudiantesEncargadoId implements Serializable {
    private static final long serialVersionUID = -6184136316684164187L;
    @Column(name = "id_estudiante", columnDefinition = "int UNSIGNED not null")
    private Long idEstudiante;

    @Column(name = "id_encargado", columnDefinition = "int UNSIGNED not null")
    private Long idEncargado;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        EstudiantesEncargadoId entity = (EstudiantesEncargadoId) o;
        return Objects.equals(this.idEncargado, entity.idEncargado) &&
                Objects.equals(this.idEstudiante, entity.idEstudiante);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idEncargado, idEstudiante);
    }

}