package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.CodigoVerificacion.Proposito;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.RoleRepository;
import cr.acusab.una.sigac.data.repository.TokenRevocadoRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.DemasiadosCodigosException;
import cr.acusab.una.sigac.logic.service.AuthService;
import cr.acusab.una.sigac.logic.service.CodigoVerificacionService;
import cr.acusab.una.sigac.logic.service.EmailService;
import cr.acusab.una.sigac.logic.service.JwtService;
import cr.acusab.una.sigac.logic.service.LimiteSolicitudesRecuperacion;
import cr.acusab.una.sigac.logic.service.LimiteSolicitudesRecuperacion.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * «Olvidé mi contraseña» con el límite por cédula: el 429 sale antes de
 * buscar el usuario y las respuestas no dependen de si la cédula existe.
 */
class RecuperacionLimiteAuthServiceTest {

    private static final String REGISTRADA = "100000878";
    private static final String INEXISTENTE = "999999999";

    private RelojDePrueba reloj;
    private UsuarioRepository usuarioRepository;
    private CodigoVerificacionService codigos;
    private EmailService email;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        reloj = new RelojDePrueba(Instant.parse("2026-11-26T22:00:00Z"));
        usuarioRepository = mock(UsuarioRepository.class);
        codigos = mock(CodigoVerificacionService.class);
        email = mock(EmailService.class);
        auth = new AuthService(mock(AuthenticationManager.class), mock(JwtService.class), usuarioRepository,
                mock(RoleRepository.class), mock(TokenRevocadoRepository.class), codigos, email,
                mock(PasswordEncoder.class), new LimiteSolicitudesRecuperacion(reloj));

        Usuario usuario = new Usuario();
        usuario.setId(91L);
        usuario.setCedula(REGISTRADA);
        usuario.setNombre("Luis Prueba");
        usuario.setCorreo("luis@test.cr");
        when(usuarioRepository.findByCedula(REGISTRADA)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByCedula(INEXISTENTE)).thenReturn(Optional.empty());
        when(codigos.generar(any(), any())).thenReturn("123456");
    }

    @Test
    void segundaSolicitudEnElMismoMinutoDa429SinBuscarElUsuario() {
        auth.solicitarCodigoRecuperacion(REGISTRADA);
        reloj.avanzar(Duration.ofSeconds(30));

        DemasiadosCodigosException error = assertThrows(DemasiadosCodigosException.class,
                () -> auth.solicitarCodigoRecuperacion(REGISTRADA));

        assertEquals("Espere un momento antes de pedir otro código.", error.getMessage());
        assertEquals(Instant.parse("2026-11-26T22:01:00Z"), error.getProximoEn());
        verify(usuarioRepository, times(1)).findByCedula(REGISTRADA);
    }

    @Test
    void sextaEnUnaHoraDa429ConProximoEnYNoEnviaCorreo() {
        List<Integer> restantes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            restantes.add(auth.solicitarCodigoRecuperacion(REGISTRADA).restantes());
            reloj.avanzar(Duration.ofMinutes(1));
        }
        assertEquals(List.of(4, 3, 2, 1, 0), restantes);
        verify(email, times(5)).enviarCodigoRecuperacion(anyString(), anyString(), anyString(), anyString(), anyInt());

        DemasiadosCodigosException error = assertThrows(DemasiadosCodigosException.class,
                () -> auth.solicitarCodigoRecuperacion(REGISTRADA));

        assertEquals("Ya pidió 5 códigos en la última hora.", error.getMessage());
        assertEquals(0, error.getRestantes());
        assertEquals(Instant.parse("2026-11-26T23:00:00Z"), error.getProximoEn());
        verify(email, times(5)).enviarCodigoRecuperacion(anyString(), anyString(), anyString(), anyString(), anyInt());
        verify(codigos, times(5)).generar(any(), any(Proposito.class));
    }

    @Test
    void cedulaRegistradaEInexistenteDanLasMismasRespuestas() {
        List<String> registrada = new ArrayList<>();
        List<String> inexistente = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            registrada.add(respuesta(REGISTRADA));
            inexistente.add(respuesta(INEXISTENTE));
            reloj.avanzar(Duration.ofSeconds(i == 2 ? 20 : 61));
        }

        assertEquals(registrada, inexistente);
    }

    // Lo que vería la pantalla: resultado permitido o el 429 con sus datos
    private String respuesta(String cedula) {
        try {
            Resultado r = auth.solicitarCodigoRecuperacion(cedula);
            return "200 restantes=" + r.restantes() + " proximoEn=" + r.proximoEn();
        } catch (DemasiadosCodigosException e) {
            return "429 " + e.getMessage() + " restantes=" + e.getRestantes() + " proximoEn=" + e.getProximoEn();
        }
    }
}
