package cr.acusab.una.sigac.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import cr.acusab.una.sigac.data.repository.TokenRevocadoRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.service.AuthService;
import cr.acusab.una.sigac.logic.service.JwtService;
import cr.acusab.una.sigac.presentation.dto.response.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Respuestas de seguridad cuando la sesión vence o no alcanza:
 * 401 SESION_EXPIRADA / SIN_SESION, 403 por rol, y las rutas públicas
 * siguen funcionando aunque llegue un token vencido.
 *
 * GET /cursos es solo de DIRECTOR_ACADEMICO; se usa como ruta protegida.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SesionExpiradaTest {

    private static final String CEDULA = "118760542";
    private static final String RUTA_PROTEGIDA = "/cursos";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Value("${security.jwt.secret-key}")
    private String secreto;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private TokenRevocadoRepository tokenRevocadoRepository;

    @MockitoBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(usuarioRepository.findCodigoEstadoByCedula(CEDULA)).thenReturn(Optional.of("ACTIVO"));
        when(tokenRevocadoRepository.existsByJti(anyString())).thenReturn(false);
    }

    @Test
    void tokenVencidoEnRutaProtegidaDevuelve401SesionExpirada() throws Exception {
        mockMvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer " + tokenVencido()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("SESION_EXPIRADA"))
                .andExpect(jsonPath("$.message").value("Su sesión expiró. Inicie sesión de nuevo."));
    }

    @Test
    void tokenMalformadoDevuelve401SesionExpirada() throws Exception {
        mockMvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer no-es-un-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_EXPIRADA"));
    }

    @Test
    void tokenRevocadoDevuelve401SesionExpirada() throws Exception {
        when(tokenRevocadoRepository.existsByJti(anyString())).thenReturn(true);

        mockMvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer " + token("DIRECTOR_ACADEMICO")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_EXPIRADA"));
    }

    @Test
    void cedulaQueYaNoExisteDevuelve401SesionExpirada() throws Exception {
        when(usuarioRepository.findCodigoEstadoByCedula(CEDULA)).thenReturn(Optional.empty());

        mockMvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer " + token("DIRECTOR_ACADEMICO")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESION_EXPIRADA"));
    }

    @Test
    void sinTokenEnRutaProtegidaDevuelve401SinSesion() throws Exception {
        mockMvc.perform(get(RUTA_PROTEGIDA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("SIN_SESION"))
                .andExpect(jsonPath("$.message").value("Debe iniciar sesión."));
    }

    @Test
    void tokenValidoConOtroRolDevuelve403() throws Exception {
        mockMvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer " + token("ESTUDIANTE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("No tiene permisos para realizar esta acción."))
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    @Test
    void tokenVencidoEnLoginNoImpideIniciarSesion() throws Exception {
        when(authService.login(any())).thenReturn(
                new LoginResponse("nuevo-token", 3600000, "ESTUDIANTE", "Ana", "ana@acusab.ed.cr", false));

        mockMvc.perform(post("/auth/login")
                        .header("Authorization", "Bearer " + tokenVencido())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cedula\":\"" + CEDULA + "\",\"contrasena\":\"Clave-Segura-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("nuevo-token"));
    }

    @Test
    void tokenVencidoEnLogoutNoDa401() throws Exception {
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + tokenVencido()))
                .andExpect(status().isNoContent());
    }

    @Test
    void contrasenaTemporalSigueDando403ConSuMensaje() throws Exception {
        String temporal = jwtService.generarToken(CEDULA, "DIRECTOR_ACADEMICO", true);

        mockMvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer " + temporal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("Debe cambiar su contraseña temporal antes de usar el sistema."));
    }

    @Test
    void cuentaInactivaSigueDando401ConSuMensaje() throws Exception {
        when(usuarioRepository.findCodigoEstadoByCedula(CEDULA)).thenReturn(Optional.of("INACTIVO"));

        mockMvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer " + token("DIRECTOR_ACADEMICO")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Su cuenta está inactiva. Contacte a la administración."))
                .andExpect(jsonPath("$.codigo").doesNotExist());
    }

    private String token(String rol) {
        return jwtService.generarToken(CEDULA, rol, false);
    }

    // Firmado con la clave de pruebas, pero vencido hace una hora.
    private String tokenVencido() throws Exception {
        Instant ahora = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(CEDULA)
                .claim("rol", "DIRECTOR_ACADEMICO")
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(ahora.minusSeconds(7200)))
                .expirationTime(Date.from(ahora.minusSeconds(3600)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secreto.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
