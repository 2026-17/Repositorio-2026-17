package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.TokensRevocado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface TokenRevocadoRepository extends JpaRepository<TokensRevocado, Long> {
    boolean existsByJti(String jti);
    void deleteByExpiraEnBefore(Instant instante);
}