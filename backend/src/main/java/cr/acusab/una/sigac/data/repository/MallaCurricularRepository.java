package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.MallaCurricular;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MallaCurricularRepository
        extends JpaRepository<MallaCurricular, Long> {

    /*
     * HU-028 / HU-030
     *
     * Obtiene el último orden utilizado dentro de un nivel
     * de un plan de estudio.
     *
     * Se utiliza para colocar un curso al final del nivel
     * sin generar posiciones repetidas.
     */
    @Query("""
            SELECT MAX(m.ordenEnNivel)
            FROM MallaCurricular m
            WHERE m.idPlanEstudio = :idPlanEstudio
              AND m.nivel = :nivel
            """)
    Optional<Short> findMaxOrdenEnNivel(
            @Param("idPlanEstudio") Long idPlanEstudio,
            @Param("nivel") Short nivel
    );

    /*
     * HU-031 / HU-030
     *
     * Busca todas las asociaciones activas que tiene un curso
     * dentro de la malla curricular.
     *
     * HU-031 lo utiliza para desactivar las asociaciones.
     * HU-030 lo utilizará para conocer la ubicación académica
     * actual del curso antes de modificarla.
     */
    List<MallaCurricular> findByIdCursoAndActivoTrue(Long idCurso);

    /*
     * HU-030 - Modificar curso.
     *
     * Busca si el curso ya posee una asociación con un plan
     * de estudio determinado, independientemente de si dicha
     * asociación está activa o inactiva.
     *
     * Esto es importante porque la base de datos posee la
     * restricción única:
     *
     *     (id_plan_estudio, id_curso)
     *
     * Por lo tanto, si al cambiar el programa académico el curso
     * ya estuvo anteriormente asociado con el plan destino, no
     * podemos insertar otra fila igual. El servicio podrá reutilizar
     * correctamente la asociación existente.
     */
    Optional<MallaCurricular> findByIdPlanEstudioAndIdCurso(
            Long idPlanEstudio,
            Long idCurso
    );
}