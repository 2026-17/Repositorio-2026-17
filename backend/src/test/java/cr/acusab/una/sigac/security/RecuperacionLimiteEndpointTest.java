package cr.acusab.una.sigac.security;

import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.service.CodigoVerificacionService;
import cr.acusab.una.sigac.logic.service.EmailService;
import cr.acusab.una.sigac.unit.RelojDePrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * POST /auth/recuperar-contrasena/solicitar con el límite por cédula:
 * cuerpo del 200 y del 429 DEMASIADOS_CODIGOS tal como lo recibe la pantalla.
 * Cada prueba usa una cédula distinta (el límite vive en memoria).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecuperacionLimiteEndpointTest {

    private static final String RUTA = "/auth/recuperar-contrasena/solicitar";
    private static final String MENSAJE =
            "Si la cédula está registrada, recibirá un código en el correo de la cuenta en unos minutos.";

    @TestConfiguration
    static class Reloj {
        @Bean
        RelojDePrueba relojDePrueba() {
            return new RelojDePrueba(Instant.parse("2026-11-26T22:00:00Z"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RelojDePrueba reloj;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private CodigoVerificacionService codigoVerificacionService;

    @MockitoBean
    private EmailService emailService;

    @Test
    void elDoscientosTraeElMensajeDeSiempreRestantesYProximoEn() throws Exception {
        Instant ahora = reloj.instant();

        pedir("111111111")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(MENSAJE))
                .andExpect(jsonPath("$.restantes").value(4))
                .andExpect(jsonPath("$.proximoEn").value(ahora.plus(Duration.ofMinutes(1)).toString()));
    }

    @Test
    void segundaEnElMismoMinutoDa429DemasiadosCodigos() throws Exception {
        pedir("222222222").andExpect(status().isOk());
        reloj.avanzar(Duration.ofSeconds(10));

        pedir("2-2222-2222")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_CODIGOS"))
                .andExpect(jsonPath("$.message").value("Espere un momento antes de pedir otro código."))
                .andExpect(jsonPath("$.restantes").value(4))
                .andExpect(jsonPath("$.proximoEn").exists());
    }

    @Test
    void sextaEnLaHoraDa429ConLaHoraEnQuePuedePedirOtroYSinCorreo() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        usuario.setCedula("333333333");
        usuario.setNombre("Ana Prueba");
        usuario.setCorreo("ana@test.cr");
        when(usuarioRepository.findByCedula("333333333")).thenReturn(Optional.of(usuario));
        when(codigoVerificacionService.generar(any(), any())).thenReturn("123456");

        Instant primera = reloj.instant();
        for (int i = 0; i < 5; i++) {
            pedir("333333333").andExpect(status().isOk()).andExpect(jsonPath("$.restantes").value(4 - i));
            reloj.avanzar(Duration.ofMinutes(1));
        }
        org.mockito.Mockito.clearInvocations(emailService);

        pedir("333333333")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_CODIGOS"))
                .andExpect(jsonPath("$.message").value("Ya pidió 5 códigos en la última hora."))
                .andExpect(jsonPath("$.restantes").value(0))
                .andExpect(jsonPath("$.proximoEn").value(primera.plus(Duration.ofHours(1)).toString()));

        verify(emailService, never()).enviarCodigoRecuperacion(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void cedulaRegistradaYQueNoExisteDanElMismoCuerpo() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setId(8L);
        usuario.setCedula("444444444");
        usuario.setNombre("Eva Prueba");
        usuario.setCorreo("eva@test.cr");
        when(usuarioRepository.findByCedula("444444444")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByCedula("555555555")).thenReturn(Optional.empty());
        when(codigoVerificacionService.generar(any(), any())).thenReturn("123456");

        for (int i = 0; i < 3; i++) {
            String registrada = pedir("444444444").andReturn().getResponse().getContentAsString();
            String inexistente = pedir("555555555").andReturn().getResponse().getContentAsString();
            org.junit.jupiter.api.Assertions.assertEquals(registrada, inexistente);
            reloj.avanzar(Duration.ofSeconds(i == 0 ? 15 : 61));
        }
    }

    private ResultActions pedir(String cedula) throws Exception {
        return mockMvc.perform(post(RUTA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cedula\":\"" + cedula + "\"}"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
