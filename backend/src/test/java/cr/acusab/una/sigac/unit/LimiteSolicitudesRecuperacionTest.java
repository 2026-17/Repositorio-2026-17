package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.logic.service.LimiteSolicitudesRecuperacion;
import cr.acusab.una.sigac.logic.service.LimiteSolicitudesRecuperacion.Motivo;
import cr.acusab.una.sigac.logic.service.LimiteSolicitudesRecuperacion.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * Límite de «Olvidé mi contraseña» por cédula: 1 por minuto y 5 por hora.
 */
class LimiteSolicitudesRecuperacionTest {

    private static final Instant INICIO = Instant.parse("2026-11-26T22:00:00Z");
    private static final Duration MINUTO = Duration.ofMinutes(1);

    private RelojDePrueba reloj;
    private LimiteSolicitudesRecuperacion limite;

    @BeforeEach
    void setUp() {
        reloj = new RelojDePrueba(INICIO);
        limite = new LimiteSolicitudesRecuperacion(reloj);
    }

    @Test
    void segundaSolicitudEnElMismoMinutoSeBloqueaPorElMinuto() {
        Resultado primera = limite.intentar("100000878");
        reloj.avanzar(Duration.ofSeconds(20));
        Resultado segunda = limite.intentar("100000878");

        assertTrue(primera.permitido());
        assertEquals(INICIO.plus(MINUTO), primera.proximoEn());
        assertFalse(segunda.permitido());
        assertEquals(Motivo.MINUTO, segunda.motivo());
        assertEquals(4, segunda.restantes());
        assertEquals(INICIO.plus(MINUTO), segunda.proximoEn());
    }

    @Test
    void restantesBaja4_3_2_1_0YLaSextaSeBloqueaPorLaHora() {
        List<Integer> restantes = new ArrayList<>();
        Resultado ultimo = null;
        for (int i = 0; i < 5; i++) {
            ultimo = limite.intentar("100000878");
            assertTrue(ultimo.permitido());
            restantes.add(ultimo.restantes());
            reloj.avanzar(MINUTO);
        }
        assertEquals(List.of(4, 3, 2, 1, 0), restantes);
        // Con 0 restantes, el próximo es cuando se libera el primero de la hora
        assertEquals(INICIO.plus(Duration.ofHours(1)), ultimo.proximoEn());

        Resultado sexta = limite.intentar("100000878");

        assertFalse(sexta.permitido());
        assertEquals(Motivo.HORA, sexta.motivo());
        assertEquals(0, sexta.restantes());
        assertEquals(INICIO.plus(Duration.ofHours(1)), sexta.proximoEn());
    }

    @Test
    void unIntentoBloqueadoNoAlargaLaEspera() {
        limite.intentar("100000878");
        reloj.avanzar(Duration.ofSeconds(50));
        assertFalse(limite.intentar("100000878").permitido());

        reloj.avanzar(Duration.ofSeconds(10));

        assertTrue(limite.intentar("100000878").permitido());
    }

    @Test
    void alPasarLaHoraSeLiberaElPrimerCodigo() {
        for (int i = 0; i < 5; i++) {
            limite.intentar("100000878");
            reloj.avanzar(MINUTO);
        }
        reloj.avanzar(Duration.ofMinutes(55)); // justo una hora después de la primera

        Resultado otra = limite.intentar("100000878");

        assertTrue(otra.permitido());
        assertEquals(0, otra.restantes());
        assertEquals(INICIO.plus(MINUTO).plus(Duration.ofHours(1)), otra.proximoEn());
    }

    @Test
    void cedulasConYSinGuionesCuentanComoLaMisma() {
        assertTrue(limite.intentar("1-0000-0878").permitido());
        reloj.avanzar(Duration.ofSeconds(5));

        Resultado otra = limite.intentar(" 100000878 ");

        assertFalse(otra.permitido());
        assertEquals(Motivo.MINUTO, otra.motivo());
    }

    @Test
    void cedulasDistintasNoSeAfectan() {
        assertTrue(limite.intentar("100000878").permitido());
        assertTrue(limite.intentar("200000999").permitido());
    }

    @Test
    void lasCedulasSinSolicitudesRecientesSalenDeLaMemoria() throws Exception {
        limite.intentar("100000878");
        limite.intentar("200000999");
        reloj.avanzar(Duration.ofHours(2));

        limite.intentar("300000111");

        var metodo = LimiteSolicitudesRecuperacion.class.getDeclaredMethod("cedulasEnMemoria");
        metodo.setAccessible(true);
        assertEquals(1, metodo.invoke(limite));
    }

    @Test
    void conVariosHilosSoloSePermiteUnaPorMinuto() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        try {
            List<Future<Resultado>> futuros = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                futuros.add(hilos.submit(() -> limite.intentar("100000878")));
            }
            int permitidas = 0;
            for (Future<Resultado> futuro : futuros) {
                if (futuro.get().permitido()) permitidas++;
            }
            assertEquals(1, permitidas);
        } finally {
            hilos.shutdownNow();
        }
    }
}
