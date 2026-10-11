package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.CodigoVerificacion;
import cr.acusab.una.sigac.data.entity.CodigoVerificacion.Proposito;
import cr.acusab.una.sigac.data.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface CodigoVerificacionRepository extends JpaRepository<CodigoVerificacion, Long> {
    Optional<CodigoVerificacion> findFirstByIdUsuarioAndPropositoOrderByIdDesc(Usuario usuario, Proposito proposito);
    long countByIdUsuarioAndPropositoAndCreadoEnAfter(Usuario usuario, Proposito proposito, Instant desde);
    void deleteByExpiraEnBefore(Instant instante);
}
