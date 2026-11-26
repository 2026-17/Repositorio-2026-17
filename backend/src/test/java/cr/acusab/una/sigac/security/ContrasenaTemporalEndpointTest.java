package cr.acusab.una.sigac.security;

import cr.acusab.una.sigac.logic.service.AccesoEstudianteService;
import cr.acusab.una.sigac.presentation.dto.response.ContrasenaTemporalResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * POST /estudiantes/{id}/contrasena-temporal: solo ADMINISTRATIVO, y la
 * respuesta no se guarda en caché.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ContrasenaTemporalEndpointTest {

    private static final String RUTA = "/estudiantes/5/contrasena-temporal";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccesoEstudianteService accesoEstudianteService;

    @Test
    @WithMockUser(username = "111111111", roles = "ADMINISTRATIVO")
    void administrativoRecibeLaContrasenaSinCache() throws Exception {
        when(accesoEstudianteService.generarContrasenaTemporal(eq(5L), any()))
                .thenReturn(new ContrasenaTemporalResponse("Abc-2345defg", Instant.parse("2026-11-26T20:00:00Z")));

        mockMvc.perform(post(RUTA))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.contrasenaTemporal").value("Abc-2345defg"))
                .andExpect(jsonPath("$.generadaEn").exists());

        verify(accesoEstudianteService).generarContrasenaTemporal(5L, "111111111");
    }

    @Test
    @WithMockUser(username = "222222222", roles = "DIRECTOR_ACADEMICO")
    void otroRolRecibe403() throws Exception {
        mockMvc.perform(post(RUTA))
                .andExpect(status().isForbidden());

        verifyNoInteractions(accesoEstudianteService);
    }

    @Test
    @WithMockUser(username = "333333333", roles = "ESTUDIANTE")
    void estudianteRecibe403() throws Exception {
        mockMvc.perform(post(RUTA))
                .andExpect(status().isForbidden());

        verifyNoInteractions(accesoEstudianteService);
    }
}
