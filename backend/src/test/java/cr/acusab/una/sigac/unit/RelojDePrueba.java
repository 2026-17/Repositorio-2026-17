package cr.acusab.una.sigac.unit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/*
 * Reloj que se adelanta a mano, para probar límites de tiempo sin esperar
 * minutos reales.
 */
public class RelojDePrueba extends Clock {

    private volatile Instant ahora;

    public RelojDePrueba(Instant inicio) {
        this.ahora = inicio;
    }

    public void avanzar(Duration duracion) {
        ahora = ahora.plus(duracion);
    }

    @Override
    public Instant instant() {
        return ahora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
