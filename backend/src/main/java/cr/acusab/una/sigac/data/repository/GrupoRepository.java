package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GrupoRepository extends JpaRepository<Grupo, Long> {

    /*
     * HU-031 - Listado de cursos activos.
     *
     * Un curso puede tener varios grupos (distintos periodos).
     * Se utiliza el mas reciente como representativo para mostrar
     * docente y cupos en el listado.
     */
    Optional<Grupo> findFirstByIdCursoOrderByCreadoEnDesc(Long idCurso);

    List<Grupo> findByIdCursoInOrderByCreadoEnDesc(List<Long> idCursos);
}