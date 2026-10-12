package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.CodigoVerificacion;
import cr.acusab.una.sigac.data.entity.CodigoVerificacion.Proposito;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.CodigoVerificacionRepository;
import cr.acusab.una.sigac.logic.service.CodigoVerificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodigoVerificacionServiceTest {

    @Mock
    private CodigoVerificacionRepository codigoVerificacionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private CodigoVerificacionService codigoVerificacionService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {

        codigoVerificacionService =
                new CodigoVerificacionService(
                        codigoVerificacionRepository,
                        passwordEncoder
                );

        usuario = new Usuario();
        usuario.setId(1L);
        usuario.setCedula("118760542");
        usuario.setNombre("Marcela Vargas");
        usuario.setCorreo("marcela@test.cr");
        usuario.setHashContrasena("hashActual");
    }

    /*
     * ============================================================
     * PRUEBAS DE GENERACIÓN DEL CÓDIGO
     * ============================================================
     */

    @Test
    void generar_debeCrearCodigoDeSeisDigitosYGuardarSoloHash() {

        when(
                codigoVerificacionRepository
                        .countByIdUsuarioAndPropositoAndCreadoEnAfter(
                                eq(usuario),
                                eq(Proposito.RECUPERACION),
                                any(Instant.class)
                        )
        ).thenReturn(0L, 0L);

        when(passwordEncoder.encode(anyString()))
                .thenReturn("hash-del-codigo");

        String codigo =
                codigoVerificacionService.generar(
                        usuario,
                        Proposito.RECUPERACION
                );

        assertNotNull(codigo);

        assertTrue(
                codigo.matches("\\d{6}"),
                "El código debe contener exactamente 6 dígitos"
        );

        verify(passwordEncoder)
                .encode(codigo);

        ArgumentCaptor<CodigoVerificacion> captor =
                ArgumentCaptor.forClass(
                        CodigoVerificacion.class
                );

        verify(codigoVerificacionRepository)
                .save(captor.capture());

        CodigoVerificacion guardado =
                captor.getValue();

        assertEquals(
                usuario,
                guardado.getIdUsuario()
        );

        assertEquals(
                Proposito.RECUPERACION,
                guardado.getProposito()
        );

        assertEquals(
                "hash-del-codigo",
                guardado.getHashCodigo()
        );

        assertEquals(
                Short.valueOf((short) 0),
                guardado.getIntentos()
        );

        assertNull(
                guardado.getUsadoEn()
        );

        assertNotNull(
                guardado.getCreadoEn()
        );

        assertNotNull(
                guardado.getExpiraEn()
        );

        assertNotEquals(
                codigo,
                guardado.getHashCodigo(),
                "El código no debe almacenarse en texto plano"
        );
    }

    @Test
    void generar_debeConfigurarVigenciaMaximaDeDiezMinutos() {

        when(
                codigoVerificacionRepository
                        .countByIdUsuarioAndPropositoAndCreadoEnAfter(
                                eq(usuario),
                                eq(Proposito.CAMBIO),
                                any(Instant.class)
                        )
        ).thenReturn(0L, 0L);

        when(passwordEncoder.encode(anyString()))
                .thenReturn("hash");

        codigoVerificacionService.generar(
                usuario,
                Proposito.CAMBIO
        );

        ArgumentCaptor<CodigoVerificacion> captor =
                ArgumentCaptor.forClass(
                        CodigoVerificacion.class
                );

        verify(codigoVerificacionRepository)
                .save(captor.capture());

        CodigoVerificacion guardado =
                captor.getValue();

        Duration vigencia =
                Duration.between(
                        guardado.getCreadoEn(),
                        guardado.getExpiraEn()
                );

        assertEquals(
                Duration.ofMinutes(10),
                vigencia,
                "La HU-036 exige una vigencia máxima de 10 minutos"
        );
    }

    @Test
    void generar_debeGuardarFechaDeCreacion() {

        when(
                codigoVerificacionRepository
                        .countByIdUsuarioAndPropositoAndCreadoEnAfter(
                                eq(usuario),
                                eq(Proposito.RECUPERACION),
                                any(Instant.class)
                        )
        ).thenReturn(0L, 0L);

        when(passwordEncoder.encode(anyString()))
                .thenReturn("hash");

        codigoVerificacionService.generar(
                usuario,
                Proposito.RECUPERACION
        );

        ArgumentCaptor<CodigoVerificacion> captor =
                ArgumentCaptor.forClass(
                        CodigoVerificacion.class
                );

        verify(codigoVerificacionRepository)
                .save(captor.capture());

        CodigoVerificacion guardado =
                captor.getValue();

        assertNotNull(
                guardado.getCreadoEn()
        );

        assertTrue(
                guardado.getCreadoEn()
                        .isBefore(
                                Instant.now().plusSeconds(1)
                        )
        );
    }

    /*
     * ============================================================
     * PRUEBAS DE LÍMITE DE SOLICITUDES
     * ============================================================
     */

    @Test
    void generar_siYaSeSolicitoCodigoDuranteUltimoMinuto_debeRechazar() {

        when(
                codigoVerificacionRepository
                        .countByIdUsuarioAndPropositoAndCreadoEnAfter(
                                eq(usuario),
                                eq(Proposito.RECUPERACION),
                                any(Instant.class)
                        )
        ).thenReturn(1L);

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                codigoVerificacionService.generar(
                                        usuario,
                                        Proposito.RECUPERACION
                                )
                );

        assertEquals(
                HttpStatus.TOO_MANY_REQUESTS,
                ex.getStatusCode()
        );

        verify(
                codigoVerificacionRepository,
                never()
        ).save(any());

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void generar_siAlcanzoMaximoDeSolicitudesPorHora_debeRechazar() {

        when(
                codigoVerificacionRepository
                        .countByIdUsuarioAndPropositoAndCreadoEnAfter(
                                eq(usuario),
                                eq(Proposito.CAMBIO),
                                any(Instant.class)
                        )
        ).thenReturn(
                0L,
                5L
        );

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                codigoVerificacionService.generar(
                                        usuario,
                                        Proposito.CAMBIO
                                )
                );

        assertEquals(
                HttpStatus.TOO_MANY_REQUESTS,
                ex.getStatusCode()
        );

        verify(
                codigoVerificacionRepository,
                never()
        ).save(any());

        verifyNoInteractions(passwordEncoder);
    }

    /*
     * ============================================================
     * PRUEBAS DE VALIDACIÓN
     * ============================================================
     */

    @Test
    void validar_siNoExisteCodigo_debeRechazar() {

        when(
                codigoVerificacionRepository
                        .findFirstByIdUsuarioAndPropositoOrderByIdDesc(
                                usuario,
                                Proposito.RECUPERACION
                        )
        ).thenReturn(
                Optional.empty()
        );

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                codigoVerificacionService.validar(
                                        usuario,
                                        Proposito.RECUPERACION,
                                        "123456"
                                )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                ex.getStatusCode()
        );

        assertEquals(
                CodigoVerificacionService.CODIGO_INVALIDO,
                ex.getReason()
        );

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void validar_siCodigoEstaExpirado_debeRechazar() {

        CodigoVerificacion codigo =
                crearCodigo(
                        Proposito.RECUPERACION,
                        Instant.now().minusSeconds(1),
                        null,
                        (short) 0
                );

        when(
                codigoVerificacionRepository
                        .findFirstByIdUsuarioAndPropositoOrderByIdDesc(
                                usuario,
                                Proposito.RECUPERACION
                        )
        ).thenReturn(
                Optional.of(codigo)
        );

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                codigoVerificacionService.validar(
                                        usuario,
                                        Proposito.RECUPERACION,
                                        "123456"
                                )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                ex.getStatusCode()
        );

        assertEquals(
                CodigoVerificacionService.CODIGO_INVALIDO,
                ex.getReason()
        );

        verify(
                passwordEncoder,
                never()
        ).matches(
                anyString(),
                anyString()
        );

        verify(
                codigoVerificacionRepository,
                never()
        ).save(any());
    }

    @Test
    void validar_siCodigoYaFueUtilizado_debeRechazar() {

        CodigoVerificacion codigo =
                crearCodigo(
                        Proposito.CAMBIO,
                        Instant.now().plusSeconds(600),
                        Instant.now().minusSeconds(20),
                        (short) 0
                );

        when(
                codigoVerificacionRepository
                        .findFirstByIdUsuarioAndPropositoOrderByIdDesc(
                                usuario,
                                Proposito.CAMBIO
                        )
        ).thenReturn(
                Optional.of(codigo)
        );

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                codigoVerificacionService.validar(
                                        usuario,
                                        Proposito.CAMBIO,
                                        "123456"
                                )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                ex.getStatusCode()
        );

        assertEquals(
                CodigoVerificacionService.CODIGO_INVALIDO,
                ex.getReason()
        );

        verify(
                passwordEncoder,
                never()
        ).matches(
                anyString(),
                anyString()
        );

        verify(
                codigoVerificacionRepository,
                never()
        ).save(any());
    }

    @Test
    void validar_siAlcanzoMaximoDeIntentos_debeRechazar() {

        CodigoVerificacion codigo =
                crearCodigo(
                        Proposito.RECUPERACION,
                        Instant.now().plusSeconds(600),
                        null,
                        (short) 5
                );

        when(
                codigoVerificacionRepository
                        .findFirstByIdUsuarioAndPropositoOrderByIdDesc(
                                usuario,
                                Proposito.RECUPERACION
                        )
        ).thenReturn(
                Optional.of(codigo)
        );

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                codigoVerificacionService.validar(
                                        usuario,
                                        Proposito.RECUPERACION,
                                        "123456"
                                )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                ex.getStatusCode()
        );

        assertEquals(
                CodigoVerificacionService.CODIGO_INVALIDO,
                ex.getReason()
        );

        verify(
                passwordEncoder,
                never()
        ).matches(
                anyString(),
                anyString()
        );

        verify(
                codigoVerificacionRepository,
                never()
        ).save(any());
    }

    @Test
    void validar_siCodigoEsNull_debeIncrementarIntentosYGuardar() {

        CodigoVerificacion codigo =
                crearCodigo(
                        Proposito.CAMBIO,
                        Instant.now().plusSeconds(600),
                        null,
                        (short) 1
                );

        when(
                codigoVerificacionRepository
                        .findFirstByIdUsuarioAndPropositoOrderByIdDesc(
                                usuario,
                                Proposito.CAMBIO
                        )
        ).thenReturn(
                Optional.of(codigo)
        );

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                codigoVerificacionService.validar(
                                        usuario,
                                        Proposito.CAMBIO,
                                        null
                                )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                ex.getStatusCode()
        );

        assertEquals(
                Short.valueOf((short) 2),
                codigo.getIntentos()
        );

        verify(
                codigoVerificacionRepository
        ).save(codigo);

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void validar_siCodigoEsIncorrecto_debeIncrementarIntentosYGuardar() {

        CodigoVerificacion codigo =
                crearCodigo(
                        Proposito.RECUPERACION,
                        Instant.now().plusSeconds(600),
                        null,
                        (short) 2
                );

        when(
                codigoVerificacionRepository
                        .findFirstByIdUsuarioAndPropositoOrderByIdDesc(
                                usuario,
                                Proposito.RECUPERACION
                        )
        ).thenReturn(
                Optional.of(codigo)
        );

        when(
                passwordEncoder.matches(
                        "000000",
                        "hash-codigo"
                )
        ).thenReturn(false);

        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                codigoVerificacionService.validar(
                                        usuario,
                                        Proposito.RECUPERACION,
                                        "000000"
                                )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                ex.getStatusCode()
        );

        assertEquals(
                CodigoVerificacionService.CODIGO_INVALIDO,
                ex.getReason()
        );

        assertEquals(
                Short.valueOf((short) 3),
                codigo.getIntentos()
        );

        verify(
                passwordEncoder
        ).matches(
                "000000",
                "hash-codigo"
        );

        verify(
                codigoVerificacionRepository
        ).save(codigo);
    }

    @Test
    void validar_siCodigoEsCorrecto_debeMarcarloComoUtilizado() {

        CodigoVerificacion codigo =
                crearCodigo(
                        Proposito.CAMBIO,
                        Instant.now().plusSeconds(600),
                        null,
                        (short) 0
                );

        when(
                codigoVerificacionRepository
                        .findFirstByIdUsuarioAndPropositoOrderByIdDesc(
                                usuario,
                                Proposito.CAMBIO
                        )
        ).thenReturn(
                Optional.of(codigo)
        );

        when(
                passwordEncoder.matches(
                        "123456",
                        "hash-codigo"
                )
        ).thenReturn(true);

        codigoVerificacionService.validar(
                usuario,
                Proposito.CAMBIO,
                "123456"
        );

        assertNotNull(
                codigo.getUsadoEn()
        );

        assertEquals(
                Short.valueOf((short) 0),
                codigo.getIntentos()
        );

        verify(
                passwordEncoder
        ).matches(
                "123456",
                "hash-codigo"
        );

        verify(
                codigoVerificacionRepository
        ).save(codigo);
    }

    @Test
    void validar_codigoCorrectoNoDebeIncrementarIntentos() {

        CodigoVerificacion codigo =
                crearCodigo(
                        Proposito.RECUPERACION,
                        Instant.now().plusSeconds(600),
                        null,
                        (short) 2
                );

        when(
                codigoVerificacionRepository
                        .findFirstByIdUsuarioAndPropositoOrderByIdDesc(
                                usuario,
                                Proposito.RECUPERACION
                        )
        ).thenReturn(
                Optional.of(codigo)
        );

        when(
                passwordEncoder.matches(
                        "654321",
                        "hash-codigo"
                )
        ).thenReturn(true);

        codigoVerificacionService.validar(
                usuario,
                Proposito.RECUPERACION,
                "654321"
        );

        assertEquals(
                Short.valueOf((short) 2),
                codigo.getIntentos()
        );

        assertNotNull(
                codigo.getUsadoEn()
        );

        verify(
                codigoVerificacionRepository
        ).save(codigo);
    }

    /*
     * ============================================================
     * MÉTODO AUXILIAR
     * ============================================================
     */

    private CodigoVerificacion crearCodigo(
            Proposito proposito,
            Instant expiraEn,
            Instant usadoEn,
            short intentos
    ) {

        CodigoVerificacion codigo =
                new CodigoVerificacion();

        codigo.setId(1L);

        codigo.setIdUsuario(
                usuario
        );

        codigo.setProposito(
                proposito
        );

        codigo.setHashCodigo(
                "hash-codigo"
        );

        codigo.setIntentos(
                intentos
        );

        codigo.setCreadoEn(
                Instant.now().minusSeconds(60)
        );

        codigo.setExpiraEn(
                expiraEn
        );

        codigo.setUsadoEn(
                usadoEn
        );

        return codigo;
    }
}