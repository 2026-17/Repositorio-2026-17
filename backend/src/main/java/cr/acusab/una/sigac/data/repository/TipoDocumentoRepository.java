package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.TiposDocumento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoDocumentoRepository extends JpaRepository<TiposDocumento, Integer> {
    Optional<TiposDocumento> findByCodigo(String codigo);
}
