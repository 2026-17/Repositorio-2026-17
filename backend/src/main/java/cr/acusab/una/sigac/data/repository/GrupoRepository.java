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

    /*
     * HU-014 - Asignar profesores a cursos y grupos.
     */

    // Otros grupos del mismo profesor en el mismo periodo, para detectar
    // choques de horario (excluye el propio grupo que se esta asignando).
    List<Grupo> findByIdProfesorAndIdPeriodoAndIdNot(Long idProfesor, Long idPeriodo, Long idGrupo);

    // Grupos asignados a un profesor ("cursos asignados al profesor").
    List<Grupo> findByIdProfesor(Long idProfesor);

    // Soporta el flujo Curso -> Periodo -> Grupo al elegir a quien asignar.
    List<Grupo> findByIdCursoAndIdPeriodo(Long idCurso, Long idPeriodo);
}