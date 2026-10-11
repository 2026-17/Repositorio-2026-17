package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.repository.CodigoVerificacionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Purga periódicamente los códigos de verificación vencidos. No se borran apenas
 * vencen: CodigoVerificacionService cuenta los códigos de la última hora para
 * limitar los reenvíos, así que se conservan un día (también sirve para revisar
 * casos de "no me llegó el código").
 */
@Component
public class LimpiezaCodigosVerificacionJob {

    private static final Duration RETENCION = Duration.ofDays(1);

    private final CodigoVerificacionRepository codigoVerificacionRepository;

    public LimpiezaCodigosVerificacionJob(CodigoVerificacionRepository codigoVerificacionRepository) {
        this.codigoVerificacionRepository = codigoVerificacionRepository;
    }

    @Scheduled(fixedRate = 3600000) // cada hora
    @Transactional
    public void purgarVencidos() {
        codigoVerificacionRepository.deleteByExpiraEnBefore(Instant.now().minus(RETENCION));
    }
}
