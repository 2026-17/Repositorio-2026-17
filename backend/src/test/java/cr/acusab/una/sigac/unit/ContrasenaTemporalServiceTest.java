package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.logic.service.ContrasenaTemporalService;
import cr.acusab.una.sigac.logic.service.EmailService;
import cr.acusab.una.sigac.presentation.dto.request.PoliticaContrasena;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ContrasenaTemporalServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private EmailService emailService;
    private ContrasenaTemporalService service;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        service = new ContrasenaTemporalService(passwordEncoder, emailService);
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

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setCedula("118760542");
        usuario.setNombre("Marcela Vargas");
        usuario.setCorreo("marcela@test.cr");
        return usuario;
    }
}
