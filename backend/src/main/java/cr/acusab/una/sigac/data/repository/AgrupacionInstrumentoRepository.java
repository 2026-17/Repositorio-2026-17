package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.AgrupacionInstrumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AgrupacionInstrumentoRepository
        extends JpaRepository<AgrupacionInstrumento, AgrupacionInstrumento.Clave> {

    // Instrumentos permitidos de varias agrupaciones a la vez.
    List<AgrupacionInstrumento> findByIdAgrupacionIn(Collection<Long> idsAgrupacion);

    /*
     * Al editar la agrupación los instrumentos se reemplazan completos. El
     * borrado se ejecuta de inmediato para que los nuevos no choquen con la
     * llave primaria.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AgrupacionInstrumento a WHERE a.idAgrupacion = :idAgrupacion")
    void eliminarPorAgrupacion(@Param("idAgrupacion") Long idAgrupacion);
}
