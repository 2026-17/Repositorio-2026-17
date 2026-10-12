package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.CodigoVerificacion.Proposito;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.RoleRepository;
import cr.acusab.una.sigac.data.repository.TokenRevocadoRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.service.AuthService;
import cr.acusab.una.sigac.logic.service.CodigoVerificacionService;
import cr.acusab.una.sigac.logic.service.EmailService;
import cr.acusab.una.sigac.logic.service.JwtService;
import cr.acusab.una.sigac.presentation.dto.request.CambiarContrasenaRequest;
import cr.acusab.una.sigac.presentation.dto.request.RestablecerContrasenaRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private AuthenticationManager authenticationManager;
    private JwtService jwtService;
    private UsuarioRepository usuarioRepository;
    private RoleRepository roleRepository;
    private TokenRevocadoRepository tokenRevocadoRepository;
    private CodigoVerificacionService codigoVerificacionService;
    private EmailService emailService;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        jwtService = mock(JwtService.class);
        usuarioRepository = mock(UsuarioRepository.class);
        roleRepository = mock(RoleRepository.class);
        tokenRevocadoRepository = mock(TokenRevocadoRepository.class);
        codigoVerificacionService = mock(CodigoVerificacionService.class);
        emailService = mock(EmailService.class);
        passwordEncoder = mock(PasswordEncoder.class);

        authService = new AuthService(
                authenticationManager,
                jwtService,
                usuarioRepository,
                roleRepository,
                tokenRevocadoRepository,
                codigoVerificacionService,
                emailService,
                passwordEncoder
        );
    }

    @Test
    void solicitarCodigoRecuperacion_siUsuarioExiste_generaYEnviaCodigo() {
        Usuario usuario = usuario();

        when(usuarioRepository.findByCedula("118760542"))
                .thenReturn(Optional.of(usuario));

        when(codigoVerificacionService.generar(
                usuario,
                Proposito.RECUPERACION))
                .thenReturn("123456");

        authService.solicitarCodigoRecuperacion(" 118760542 ");

        verify(codigoVerificacionService)
                .generar(usuario, Proposito.RECUPERACION);

        verify(emailService)
                .enviarCodigoRecuperacion(
                        "marcela@test.cr",
                        "123456",
                        CodigoVerificacionService.MINUTOS_VIGENCIA
                );
    }

    @Test
    void solicitarCodigoRecuperacion_siUsuarioNoExiste_noRevelaInformacionNiEnviaCorreo() {
        when(usuarioRepository.findByCedula("999999999"))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(
                () -> authService.solicitarCodigoRecuperacion("999999999")
        );

        verifyNoInteractions(codigoVerificacionService);
        verifyNoInteractions(emailService);
    }

    @Test
    void solicitarCodigoRecuperacion_siGeneracionEsRechazada_noPropagaErrorNiEnviaCorreo() {
        Usuario usuario = usuario();

        when(usuarioRepository.findByCedula("118760542"))
                .thenReturn(Optional.of(usuario));

        when(codigoVerificacionService.generar(
                usuario,
                Proposito.RECUPERACION))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "Demasiados códigos"
                        )
                );

        assertDoesNotThrow(
                () -> authService.solicitarCodigoRecuperacion("118760542")
        );

        verify(emailService, never())
                .enviarCodigoRecuperacion(
                        any(),
                        any(),
                        anyInt()
                );
    }

    @Test
    void restablecerContrasena_conCodigoValido_actualizaContrasenaYSeguridad() {
        Usuario usuario = usuario();

        RestablecerContrasenaRequest request =
                new RestablecerContrasenaRequest(
                        " 118760542 ",
                        "123456",
                        "NuevaClave1$Aa"
                );

        when(usuarioRepository.findByCedula("118760542"))
                .thenReturn(Optional.of(usuario));

        when(passwordEncoder.matches(
                "NuevaClave1$Aa",
                "hashActual"))
                .thenReturn(false);

        when(passwordEncoder.encode("NuevaClave1$Aa"))
                .thenReturn("hashNuevo");

        authService.restablecerContrasena(request);

        verify(codigoVerificacionService)
                .validar(
                        usuario,
                        Proposito.RECUPERACION,
                        "123456"
                );

        verify(passwordEncoder)
                .encode("NuevaClave1$Aa");

        verify(usuarioRepository)
                .save(usuario);

        assertEquals(
                "hashNuevo",
                usuario.getHashContrasena()
        );

        assertFalse(
                usuario.getRequiereCambioContrasena()
        );

        assertEquals(
                (short) 0,
                usuario.getIntentosFallidos()
        );

        assertNull(
                usuario.getBloqueadoHasta()
        );

        assertNotNull(
                usuario.getActualizadoEn()
        );
    }

    @Test
    void restablecerContrasena_siUsuarioNoExiste_rechazaConMensajeGenerico() {
        RestablecerContrasenaRequest request =
                new RestablecerContrasenaRequest(
                        "999999999",
                        "123456",
                        "NuevaClave1$Aa"
                );

        when(usuarioRepository.findByCedula("999999999"))
                .thenReturn(Optional.empty());

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () -> authService.restablecerContrasena(request)
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                ex.getStatusCode()
        );

        assertEquals(
                CodigoVerificacionService.CODIGO_INVALIDO,
                ex.getReason()
        );

        verifyNoInteractions(codigoVerificacionService);

        verify(usuarioRepository, never())
                .save(any());
    }

    @Test
    void restablecerContrasena_siNuevaEsIgualALaActual_rechazaCambio() {
        Usuario usuario = usuario();

        RestablecerContrasenaRequest request =
                new RestablecerContrasenaRequest(
                        "118760542",
                        "123456",
                        "MismaClave1$Aa"
                );

        when(usuarioRepository.findByCedula("118760542"))
                .thenReturn(Optional.of(usuario));

        when(passwordEncoder.matches(
                "MismaClave1$Aa",
                "hashActual"))
                .thenReturn(true);

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () -> authService.restablecerContrasena(request)
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                ex.getStatusCode()
        );

        verify(codigoVerificacionService)
                .validar(
                        usuario,
                        Proposito.RECUPERACION,
                        "123456"
                );

        verify(passwordEncoder, never())
                .encode(any());

        verify(usuarioRepository, never())
                .save(any());
    }

    @Test
    void solicitarCodigoCambio_siUsuarioExiste_generaYEnviaCodigo() {
        Usuario usuario = usuario();

        when(usuarioRepository.findByCedula("118760542"))
                .thenReturn(Optional.of(usuario));

        when(codigoVerificacionService.generar(
                usuario,
                Proposito.CAMBIO))
                .thenReturn("654321");

        authService.solicitarCodigoCambio("118760542");

        verify(codigoVerificacionService)
                .generar(
                        usuario,
                        Proposito.CAMBIO
                );

        verify(emailService)
                .enviarCodigoCambio(
                        "marcela@test.cr",
                        "654321",
                        CodigoVerificacionService.MINUTOS_VIGENCIA
                );
    }

    @Test
    void solicitarCodigoCambio_siUsuarioNoExiste_retornaNotFound() {
        when(usuarioRepository.findByCedula("999999999"))
                .thenReturn(Optional.empty());

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () -> authService.solicitarCodigoCambio(
                                "999999999"
                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                ex.getStatusCode()
        );

        verifyNoInteractions(codigoVerificacionService);
        verifyNoInteractions(emailService);
    }

    @Test
    void cambiarContrasena_conDatosValidos_actualizaContrasena() {
        Usuario usuario = usuario();

        CambiarContrasenaRequest request =
                new CambiarContrasenaRequest(
                        "ClaveActual1$",
                        "NuevaClave1$Aa",
                        "123456"
                );

        when(usuarioRepository.findByCedula("118760542"))
                .thenReturn(Optional.of(usuario));

        when(passwordEncoder.matches(
                "ClaveActual1$",
                "hashActual"))
                .thenReturn(true);

        when(passwordEncoder.matches(
                "NuevaClave1$Aa",
                "hashActual"))
                .thenReturn(false);

        when(passwordEncoder.encode("NuevaClave1$Aa"))
                .thenReturn("hashNuevo");

        authService.cambiarContrasena(
                "118760542",
                request
        );

        verify(codigoVerificacionService)
                .validar(
                        usuario,
                        Proposito.CAMBIO,
                        "123456"
                );

        verify(passwordEncoder)
                .encode("NuevaClave1$Aa");

        verify(usuarioRepository)
                .save(usuario);

        assertEquals(
                "hashNuevo",
                usuario.getHashContrasena()
        );

        assertFalse(
                usuario.getRequiereCambioContrasena()
        );

        assertEquals(
                (short) 0,
                usuario.getIntentosFallidos()
        );

        assertNull(
                usuario.getBloqueadoHasta()
        );
    }

    @Test
    void cambiarContrasena_siCodigoEsInvalido_noCompruebaContrasenaNiGuarda() {
        Usuario usuario = usuario();

        CambiarContrasenaRequest request =
                new CambiarContrasenaRequest(
                        "ClaveActual1$",
                        "NuevaClave1$Aa",
                        "000000"
                );

        when(usuarioRepository.findByCedula("118760542"))
                .thenReturn(Optional.of(usuario));

        doThrow(
                new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        CodigoVerificacionService.CODIGO_INVALIDO
                )
        ).when(codigoVerificacionService)
                .validar(
                        usuario,
                        Proposito.CAMBIO,
                        "000000"
                );

        assertThrows(
                ResponseStatusException.class,
                () -> authService.cambiarContrasena(
                        "118760542",
                        request
                )
        );

        verifyNoInteractions(passwordEncoder);

        verify(usuarioRepository, never())
                .save(any());
    }

    @Test
    void cambiarContrasena_siContrasenaActualEsIncorrecta_rechazaYNoGuarda() {
        Usuario usuario = usuario();

        CambiarContrasenaRequest request =
                new CambiarContrasenaRequest(
                        "Incorrecta1$",
                        "NuevaClave1$Aa",
                        "123456"
                );

        when(usuarioRepository.findByCedula("118760542"))
                .thenReturn(Optional.of(usuario));

        when(passwordEncoder.matches(
                "Incorrecta1$",
                "hashActual"))
                .thenReturn(false);

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () -> authService.cambiarContrasena(
                                "118760542",
                                request
                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                ex.getStatusCode()
        );

        verify(codigoVerificacionService)
                .validar(
                        usuario,
                        Proposito.CAMBIO,
                        "123456"
                );

        verify(passwordEncoder, never())
                .encode(any());

        verify(usuarioRepository, never())
                .save(any());
    }

    @Test
    void cambiarContrasena_siNuevaEsIgualALaActual_rechazaYNoGuarda() {
        Usuario usuario = usuario();

        CambiarContrasenaRequest request =
                new CambiarContrasenaRequest(
                        "ClaveActual1$",
                        "ClaveActual1$",
                        "123456"
                );

        when(usuarioRepository.findByCedula("118760542"))
                .thenReturn(Optional.of(usuario));

        when(passwordEncoder.matches(
                "ClaveActual1$",
                "hashActual"))
                .thenReturn(true);

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () -> authService.cambiarContrasena(
                                "118760542",
                                request
                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                ex.getStatusCode()
        );

        verify(codigoVerificacionService)
                .validar(
                        usuario,
                        Proposito.CAMBIO,
                        "123456"
                );

        verify(passwordEncoder, never())
                .encode(any());

        verify(usuarioRepository, never())
                .save(any());
    }

    private Usuario usuario() {
        Usuario usuario = new Usuario();

        usuario.setId(1L);
        usuario.setCedula("118760542");
        usuario.setNombre("Marcela Vargas");
        usuario.setCorreo("marcela@test.cr");
        usuario.setHashContrasena("hashActual");
        usuario.setRequiereCambioContrasena(true);
        usuario.setIntentosFallidos((short) 3);

        usuario.setBloqueadoHasta(
                java.time.Instant.now().plusSeconds(600)
        );

        return usuario;
    }
}