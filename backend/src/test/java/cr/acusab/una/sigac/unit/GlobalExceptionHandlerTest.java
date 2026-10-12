package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.exception.DuplicateResourceException;
import cr.acusab.una.sigac.logic.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Comprueba que el manejador global no convierta las excepciones de negocio
 * en un 500 genérico y que el mensaje llegue tal cual al cliente.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ControllerDePrueba())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void businessExceptionDevuelve400ConSuMensaje() throws Exception {
        mockMvc.perform(get("/prueba/negocio"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("X"));
    }

    @Test
    void duplicateResourceExceptionDevuelve409ConSuMensaje() throws Exception {
        mockMvc.perform(get("/prueba/duplicado"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Ya existe"));
    }

    @Test
    void credencialesIncorrectasDevuelven401EnEspanol() throws Exception {
        mockMvc.perform(get("/prueba/credenciales"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Cédula o contraseña incorrectas."));
    }

    @Test
    void cuentaBloqueadaDevuelve401ConMensajeDeBloqueo() throws Exception {
        mockMvc.perform(get("/prueba/bloqueada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        "La cuenta está bloqueada temporalmente por intentos fallidos. Intente más tarde."));
    }

    @Test
    void cuentaInactivaDevuelve401ConMensajeDeCuentaInactiva() throws Exception {
        mockMvc.perform(get("/prueba/inactiva"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("La cuenta está inactiva. Contacte a la administración."));
    }

    @Test
    void idNoNumericoEnLaRutaDevuelve400() throws Exception {
        mockMvc.perform(get("/prueba/estudiantes/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El valor de 'id' no es válido."));
    }

    @Test
    void parametroFaltanteDevuelve400() throws Exception {
        mockMvc.perform(get("/prueba/busqueda"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta el parámetro 'texto'."));
    }

    @Test
    void numeroFueraDeRangoIndicaElCampo() throws Exception {
        mockMvc.perform(post("/prueba/cupos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cupos\": 70000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El valor del campo 'cupos' no es válido."));
    }

    @Test
    void textoEnCampoNumericoIndicaElCampo() throws Exception {
        mockMvc.perform(post("/prueba/cupos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cupos\": \"muchos\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El valor del campo 'cupos' no es válido."));
    }

    record CuposDePrueba(Short cupos) {
    }

    @RestController
    static class ControllerDePrueba {

        @GetMapping("/prueba/inactiva")
        String inactiva() {
            throw new DisabledException("User is disabled");
        }

        @GetMapping("/prueba/estudiantes/{id}")
        String estudiante(@PathVariable Long id) {
            return "ok";
        }

        @GetMapping("/prueba/busqueda")
        String busqueda(@RequestParam String texto) {
            return texto;
        }

        @PostMapping("/prueba/cupos")
        String cupos(@RequestBody CuposDePrueba datos) {
            return "ok";
        }

        @GetMapping("/prueba/negocio")
        String negocio() {
            throw new BusinessException("X");
        }

        @GetMapping("/prueba/duplicado")
        String duplicado() {
            throw new DuplicateResourceException("Ya existe");
        }

        @GetMapping("/prueba/credenciales")
        String credenciales() {
            throw new BadCredentialsException("Bad credentials");
        }

        @GetMapping("/prueba/bloqueada")
        String bloqueada() {
            throw new LockedException("User account is locked");
        }
    }
}
