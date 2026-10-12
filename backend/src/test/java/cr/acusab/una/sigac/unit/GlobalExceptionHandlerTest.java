package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.exception.DuplicateResourceException;
import cr.acusab.una.sigac.logic.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @RestController
    static class ControllerDePrueba {

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
