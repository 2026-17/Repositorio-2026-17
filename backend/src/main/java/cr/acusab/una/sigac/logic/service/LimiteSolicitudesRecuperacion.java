package cr.acusab.una.sigac.logic.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Límite de «Olvidé mi contraseña» por cédula solicitada, exista o no.
 * <p>
 * Como un contador en la ventanilla: anota a qué hora pidió código cada
 * cédula y no deja pedir más de uno por minuto ni más de
 * {@link CodigoVerificacionService#MAXIMO_POR_HORA} por hora. Como no mira
 * la base de datos, la respuesta es la misma para una cédula registrada que
 * para una que no existe (no revela cuáles están registradas).
 * <p>
 * Vive en memoria: se reinicia al reiniciar el backend. El límite de la base
 * de datos (CodigoVerificacionService) sigue como respaldo.
 */
@Component
public class LimiteSolicitudesRecuperacion {

    private static final Duration VENTANA = Duration.ofHours(1);
    private static final Duration ESPERA = CodigoVerificacionService.ESPERA_ENTRE_ENVIOS;
    private static final int MAXIMO = CodigoVerificacionService.MAXIMO_POR_HORA;
    // Cada cuánto se barre el mapa entero para quitar cédulas sin solicitudes recientes
    private static final Duration BARRIDO = Duration.ofMinutes(1);

    public enum Motivo { MINUTO, HORA }

    /**
     * permitido: si se registró la solicitud. motivo: por qué se bloqueó (null
     * si se permitió). restantes: códigos que aún puede pedir en esta hora.
     * proximoEn: primer momento en que puede pedir otro.
     */
    public record Resultado(boolean permitido, Motivo motivo, int restantes, Instant proximoEn) {
    }

    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> solicitudes = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> ultimoBarrido = new AtomicReference<>(Instant.EPOCH);

    @Autowired
    public LimiteSolicitudesRecuperacion(ObjectProvider<Clock> clock) {
        this(clock.getIfAvailable(Clock::systemUTC));
    }

    public LimiteSolicitudesRecuperacion(Clock clock) {
        this.clock = clock;
    }

    /**
     * Consulta el límite y, si se permite, registra la solicitud (todo en un
     * solo paso, seguro con varios hilos). Un intento bloqueado no se registra,
     * así que no alarga la espera.
     */
    public Resultado intentar(String cedula) {
        Instant ahora = clock.instant();
        barrerSiToca(ahora);

        Resultado[] resultado = new Resultado[1];
        solicitudes.compute(ReglasIdentificacion.normalizar(cedula), (clave, horas) -> {
            Deque<Instant> lista = horas == null ? new ArrayDeque<>() : horas;
            quitarViejas(lista, ahora);

            if (lista.size() >= MAXIMO) {
                resultado[0] = new Resultado(false, Motivo.HORA, 0, redondear(lista.peekFirst().plus(VENTANA)));
            } else if (!lista.isEmpty() && lista.peekLast().plus(ESPERA).isAfter(ahora)) {
                resultado[0] = new Resultado(false, Motivo.MINUTO, MAXIMO - lista.size(),
                        redondear(lista.peekLast().plus(ESPERA)));
            } else {
                lista.addLast(ahora);
                int restantes = MAXIMO - lista.size();
                Instant proximo = restantes == 0 ? lista.peekFirst().plus(VENTANA) : ahora.plus(ESPERA);
                resultado[0] = new Resultado(true, null, restantes, redondear(proximo));
            }
            return lista.isEmpty() ? null : lista;
        });
        return resultado[0];
    }

    // Cantidad de cédulas en memoria (para las pruebas)
    int cedulasEnMemoria() {
        return solicitudes.size();
    }

    // Quita las horas de más de una hora; cada lista va de la más vieja a la más nueva
    private static void quitarViejas(Deque<Instant> lista, Instant ahora) {
        Instant limite = ahora.minus(VENTANA);
        while (!lista.isEmpty() && !lista.peekFirst().isAfter(limite)) {
            lista.removeFirst();
        }
    }

    // Como mucho una vez por minuto, quita las cédulas que ya no tienen solicitudes recientes
    private void barrerSiToca(Instant ahora) {
        Instant anterior = ultimoBarrido.get();
        if (anterior.plus(BARRIDO).isAfter(ahora) || !ultimoBarrido.compareAndSet(anterior, ahora)) {
            return;
        }
        for (String clave : solicitudes.keySet()) {
            solicitudes.computeIfPresent(clave, (k, lista) -> {
                quitarViejas(lista, ahora);
                return lista.isEmpty() ? null : lista;
            });
        }
    }

    // Hacia arriba al segundo: el frontend nunca habilita el botón antes de tiempo
    private static Instant redondear(Instant instante) {
        Instant segundos = instante.truncatedTo(ChronoUnit.SECONDS);
        return segundos.equals(instante) ? segundos : segundos.plusSeconds(1);
    }
}
