package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.CodigoVerificacionRepository;
import cr.acusab.una.sigac.logic.service.ContrasenaTemporalService;
import cr.acusab.una.sigac.logic.service.EmailService;
import cr.acusab.una.sigac.presentation.dto.request.PoliticaContrasena;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ContrasenaTemporalServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private EmailService emailService;
    private CodigoVerificacionRepository codigoVerificacionRepository;
    private ContrasenaTemporalService service;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        codigoVerificacionRepository = mock(CodigoVerificacionRepository.class);
        service = new ContrasenaTemporalService(passwordEncoder, emailService, codigoVerificacionRepository);
    }

    @Test
    void asignar_guardaSoloElHashYEnviaLaContrasenaAlCorreo() {
        Usuario usuario = usuario();

        service.asignar(usuario);

        ArgumentCaptor<String> contrasena = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarContrasenaTemporal(
                eq("marcela@test.cr"), eq("Marcela Vargas"), eq("118760542"), contrasena.capture());

        assertTrue(usuario.getRequiereCambioContrasena());
        assertNotEquals(contrasena.getValue(), usuario.getHashContrasena());
        assertTrue(passwordEncoder.matches(contrasena.getValue(), usuario.getHashContrasena()));
    }

    @Test
    void asignar_generaContrasenasQueCumplenLaPoliticaYSonDistintas() {
        int cantidad = 50;
        for (int i = 0; i < cantidad; i++) {
            service.asignar(usuario());
        }

        ArgumentCaptor<String> contrasenas = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(cantidad)).enviarContrasenaTemporal(
                eq("marcela@test.cr"), eq("Marcela Vargas"), eq("118760542"), contrasenas.capture());

        Set<String> distintas = new HashSet<>();
        for (String contrasena : contrasenas.getAllValues()) {
            // Debe servir en el login (LoginRequest: 8 a 16 caracteres)
            assertTrue(contrasena.length() >= 12 && contrasena.length() <= 16, contrasena);
            assertTrue(contrasena.matches(PoliticaContrasena.REGEX), contrasena);
            assertTrue(contrasena.matches(".*[a-z].*"), contrasena);
            distintas.add(contrasena);
        }
        assertEquals(cantidad, distintas.size());
    }

    @Test
    void asignarSinCorreo_cumpleLaPoliticaYDejaLaCuentaPendienteDeCambio() {
        Usuario usuario = usuario();
        usuario.setRequiereCambioContrasena(false);
        usuario.setIntentosFallidos((short) 4);
        usuario.setBloqueadoHasta(Instant.now().plusSeconds(600));

        String contrasena = service.asignarSinCorreo(usuario);

        assertTrue(contrasena.length() >= 12 && contrasena.length() <= 16, contrasena);
        assertTrue(contrasena.matches(PoliticaContrasena.REGEX), contrasena);
        assertTrue(usuario.getRequiereCambioContrasena());
        assertEquals((short) 0, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());
        assertNotEquals(contrasena, usuario.getHashContrasena());
        assertTrue(passwordEncoder.matches(contrasena, usuario.getHashContrasena()));
    }

    @Test
    void asignarSinCorreo_invalidaLosCodigosPendientesYNoEnviaCorreo() {
        Usuario usuario = usuario();

        service.asignarSinCorreo(usuario);

        verify(codigoVerificacionRepository).invalidarPendientes(eq(usuario), any(Instant.class));
        verifyNoInteractions(emailService);
    }

    @Test
    void asignarSinCorreo_laContrasenaAnteriorDejaDeServir() {
        Usuario usuario = usuario();
        usuario.setHashContrasena(passwordEncoder.encode("Anterior-123"));

        service.asignarSinCorreo(usuario);

        assertFalse(passwordEncoder.matches("Anterior-123", usuario.getHashContrasena()));
    }

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setCedula("118760542");
        usuario.setNombre("Marcela Vargas");
        usuario.setCorreo("marcela@test.cr");
        return usuario;
    }
}
