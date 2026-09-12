package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.repository.TokenRevocadoRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Purga periódicamente los tokens revocados ya expirados (la tabla tokens_revocados
 * solo necesita retener los jti mientras el JWT original aún sería válido por fecha).
 */
@Component
public class LimpiezaTokensRevocadosJob {

    private final TokenRevocadoRepository tokenRevocadoRepository;

    public LimpiezaTokensRevocadosJob(TokenRevocadoRepository tokenRevocadoRepository) {
        this.tokenRevocadoRepository = tokenRevocadoRepository;
    }

    @Scheduled(fixedRate = 3600000) // cada hora
    @Transactional
    public void purgarExpirados() {
        tokenRevocadoRepository.deleteByExpiraEnBefore(Instant.now());
    }
}