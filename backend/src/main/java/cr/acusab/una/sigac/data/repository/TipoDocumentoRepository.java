package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.TiposDocumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TipoDocumentoRepository
        extends JpaRepository<TiposDocumento, Integer> {

    Optional<TiposDocumento> findByCodigoAndActivoTrue(String codigo);

    /*
     * HU-022:
     * Tipos de documento disponibles para adjuntar al expediente.
     */
    List<TiposDocumento> findByActivoTrueOrderByNombreAsc();
}