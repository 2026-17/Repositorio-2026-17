package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.TipoDocumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoDocumentoRepository extends JpaRepository<TipoDocumento, Integer> {
    Optional<TipoDocumento> findByCodigo(String codigo);
}
