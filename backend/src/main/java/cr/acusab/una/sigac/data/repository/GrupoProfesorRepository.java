package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.GrupoProfesor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface GrupoProfesorRepository extends JpaRepository<GrupoProfesor, GrupoProfesor.Clave> {

    // Profesores de varios grupos a la vez.
    List<GrupoProfesor> findByIdGrupoIn(Collection<Long> idsGrupo);

    // ¿El profesor da clases en ese grupo (responsable o no)?
    boolean existsByIdGrupoAndIdProfesor(Long idGrupo, Long idProfesor);

    /*
     * Al guardar el grupo los profesores se reemplazan completos. El borrado
     * se ejecuta de inmediato para que los nuevos no choquen con la llave
     * primaria.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GrupoProfesor gp WHERE gp.idGrupo = :idGrupo")
    void eliminarPorGrupo(@Param("idGrupo") Long idGrupo);
}
