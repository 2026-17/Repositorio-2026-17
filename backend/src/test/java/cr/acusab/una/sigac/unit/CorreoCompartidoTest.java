package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.*;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.DuplicateResourceException;
import cr.acusab.una.sigac.logic.service.*;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.UsuarioCreateRequest;
import cr.acusab.una.sigac.presentation.dto.response.CuentaCorreoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/*
 * La llave de una cuenta es la cédula: varias cuentas pueden compartir un
 * correo. Todos los datos son ficticios.
 */
class CorreoCompartidoTest {

    private static final String CORREO = "familia.prueba@example.com";

    private EstudianteRepository estudianteRepository;
    private UsuarioRepository usuarioRepository;
    private RoleRepository roleRepository;
    private EstadoUsuarioRepository estadoRepository;
    private InstrumentoRepository instrumentoRepository;
    private TipoDocumentoRepository tipoDocumentoRepository;
    private DistritoRepository distritoRepository;
    private InscripcionPlanEstudioService planService;
    private ContrasenaTemporalService contrasenaTemporal;
    private EstudianteService estudianteService;

    private Role rolEstudiante;
    private EstadosUsuario activo;

    @TempDir
    Path almacenamiento;

    @BeforeEach
    void setUp() {
        estudianteRepository = mock(EstudianteRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        roleRepository = mock(RoleRepository.class);
        estadoRepository = mock(EstadoUsuarioRepository.class);
        instrumentoRepository = mock(InstrumentoRepository.class);
        tipoDocumentoRepository = mock(TipoDocumentoRepository.class);
        distritoRepository = mock(DistritoRepository.class);
        planService = mock(InscripcionPlanEstudioService.class);
        contrasenaTemporal = mock(ContrasenaTemporalService.class);
        TipoIdentificacionRepository tipoRepository = mock(TipoIdentificacionRepository.class);

        estudianteService = new EstudianteService(
                estudianteRepository, mock(EncargadoRepository.class), mock(EstudianteEncargadoRepository.class),
                instrumentoRepository, mock(EstudianteInstrumentoRepository.class), mock(ProfesorRepository.class),
                mock(DocumentoEstudianteRepository.class), tipoRepository, tipoDocumentoRepository,
                mock(CentroEducativoRepository.class), usuarioRepository, roleRepository, estadoRepository,
                contrasenaTemporal, mock(BecaService.class), planService, distritoRepository,
                new CuentaUsuarioService(usuarioRepository, estadoRepository),
                mock(EncargadoService.class), mock(DocumentoEstudianteService.class));
        ReflectionTestUtils.setField(estudianteService, "storageDirectory", almacenamiento.toString());

        TiposIdentificacion cedula = new TiposIdentificacion();
        cedula.setId(1);
        cedula.setCodigo("CEDULA");
        cedula.setNombre("Cédula nacional");
        cedula.setActivo(true);
        when(tipoRepository.findByIdAndActivoTrue(1)).thenReturn(Optional.of(cedula));

        rolEstudiante = new Role();
        rolEstudiante.setId(4);
        rolEstudiante.setCodigo("ESTUDIANTE");
        rolEstudiante.setNombre("Estudiante");
        activo = new EstadosUsuario();
        activo.setId(1);
        activo.setCodigo("ACTIVO");
    }

    // ---------- Crear estudiante ----------

    @Test
    void crearEstudiante_conCorreoDeOtraCuenta_funciona() {
        prepararRegistro();
        when(usuarioRepository.findAllByCorreoIgnoreCase(CORREO))
                .thenReturn(List.of(cuenta(90L, "100000090", "Hermana Prueba")));

        estudianteService.crear(registro("100000001"), pdf());

        verify(usuarioRepository).save(argThat(u -> CORREO.equals(u.getCorreo()) && "100000001".equals(u.getCedula())));
    }

    @Test
    void crearEstudiante_conCedulaExistente_sigueFallando() {
        prepararRegistro();
        when(usuarioRepository.findByCedula("100000001"))
                .thenReturn(Optional.of(cuenta(90L, "100000001", "Otra Persona")));

        var error = assertThrows(DuplicateResourceException.class,
                () -> estudianteService.crear(registro("100000001"), pdf()));

        assertEquals("Ya existe un usuario con esa identificación", error.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    // ---------- Crear usuario ----------

    @Test
    void usuarioService_permiteCorreoRepetido() {
        UsuarioService service = new UsuarioService(usuarioRepository, roleRepository, estadoRepository, contrasenaTemporal);
        when(usuarioRepository.findByCedula("100000002")).thenReturn(Optional.empty());
        when(roleRepository.findById(4)).thenReturn(Optional.of(rolEstudiante));
        when(estadoRepository.findById(1)).thenReturn(Optional.of(activo));
        when(usuarioRepository.save(any())).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            u.setId(91L);
            return u;
        });

        UsuarioCreateRequest request = new UsuarioCreateRequest();
        request.setCedula("100000002");
        request.setNombre("Luis Prueba");
        request.setCorreo(CORREO);
        request.setIdRol(4);
        request.setIdEstadoUsuario(1);

        assertEquals(CORREO, service.crearUsuario(request).getCorreo());
    }

    // ---------- Recuperar contraseña ----------

    @Test
    void recuperacion_usaLaCuentaDeEsaCedulaAunqueElCorreoSeaCompartido() {
        CodigoVerificacionService codigos = mock(CodigoVerificacionService.class);
        EmailService email = mock(EmailService.class);
        AuthService auth = new AuthService(mock(AuthenticationManager.class), mock(JwtService.class), usuarioRepository,
                roleRepository, mock(TokenRevocadoRepository.class), codigos, email, mock(PasswordEncoder.class));

        Usuario luis = cuenta(91L, "100000878", "Luis Prueba");
        when(usuarioRepository.findByCedula("100000878")).thenReturn(Optional.of(luis));
        when(codigos.generar(eq(luis), any())).thenReturn("123456");

        auth.solicitarCodigoRecuperacion("100000878");

        verify(codigos).generar(eq(luis), any());
        verify(email).enviarCodigoRecuperacion(CORREO, "Luis Prueba", "*****0878", "123456",
                CodigoVerificacionService.MINUTOS_VIGENCIA);
        verify(usuarioRepository, never()).findAllByCorreoIgnoreCase(anyString());
    }

    @Test
    void correoDeCodigo_indicaLaCuentaYElPrimerNombreEnElAsunto() {
        EmailService email = new EmailService("clave-de-prueba", "noreply@example.com", "SIGAC Test",
                "https://example.com/logo.png", "https://example.com");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(email, "restClient", builder.build());
        servidor.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("recuperar su contraseña · Luis"),
                        containsString("Este código es para la cuenta de <strong>Luis Prueba</strong> (cédula *****0878)"))))
                .andRespond(withSuccess());

        email.enviarCodigoRecuperacion(CORREO, "Luis Prueba", "*****0878", "123456", 15);

        servidor.verify();
    }

    @Test
    void enmascarar_muestraSoloLosUltimosCuatro() {
        assertEquals("*****0878", ReglasIdentificacion.enmascarar("1-0000-0878"));
        assertEquals("*****", ReglasIdentificacion.enmascarar("1234"));
        assertEquals("*****", ReglasIdentificacion.enmascarar(null));
    }

    // ---------- Correo en uso ----------

    @Test
    void correoEnUso_devuelveLasOtrasCuentasYExcluyeLaPropia() {
        Usuario propia = cuenta(90L, "100001111", "Ana Prueba");
        Usuario hermano = cuenta(91L, "100005678", "Luis Prueba");
        Estudiante estudiante = new Estudiante();
        estudiante.setId(5L);
        estudiante.setUsuario(propia);
        when(estudianteRepository.findById(5L)).thenReturn(Optional.of(estudiante));
        when(usuarioRepository.findAllByCorreoIgnoreCase(CORREO)).thenReturn(List.of(propia, hermano));

        List<CuentaCorreoResponse> cuentas = estudianteService.cuentasConCorreo(CORREO, 5L);

        assertEquals(List.of(new CuentaCorreoResponse("Luis Prueba", "*****5678", "Estudiante")), cuentas);
    }

    @Test
    void correoEnUso_sinCorreoNoConsulta() {
        assertTrue(estudianteService.cuentasConCorreo("  ", null).isEmpty());
        verify(usuarioRepository, never()).findAllByCorreoIgnoreCase(anyString());
    }

    // ---------- Datos de apoyo (ficticios) ----------

    private void prepararRegistro() {
        Instrumento instrumento = new Instrumento();
        instrumento.setId(3);
        instrumento.setNombre("Piano");
        when(instrumentoRepository.findByIdAndActivoTrue(3)).thenReturn(Optional.of(instrumento));

        TiposDocumento poliza = new TiposDocumento();
        poliza.setId(4);
        poliza.setCodigo("POLIZA");
        when(tipoDocumentoRepository.findByCodigoAndActivoTrue("POLIZA")).thenReturn(Optional.of(poliza));

        Provincia provincia = new Provincia();
        provincia.setId(1);
        Canton canton = new Canton();
        canton.setCodigo(101);
        canton.setProvincia(provincia);
        Distrito distrito = new Distrito();
        distrito.setCodigo(10101);
        distrito.setCanton(canton);
        when(distritoRepository.findById(anyInt())).thenReturn(Optional.of(distrito));

        when(roleRepository.findByCodigoIgnoreCase("ESTUDIANTE")).thenReturn(Optional.of(rolEstudiante));
        when(estadoRepository.findAll()).thenReturn(List.of(activo));
        when(usuarioRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(estudianteRepository.save(any())).thenAnswer(i -> {
            Estudiante e = i.getArgument(0);
            e.setId(5L);
            return e;
        });
        when(planService.sincronizar(anyLong(), any())).thenReturn(List.of(1L));
    }

    private EstudianteCreateRequest registro(String cedula) {
        return new EstudianteCreateRequest(1, cedula, "Ana", "Prueba", null, LocalDate.now().minusYears(25),
                "FEMENINO", 10101, "Dirección de prueba", null, CORREO, "Universitaria", null,
                null, 3, null, List.of(1L), List.of(), null);
    }

    private Usuario cuenta(Long id, String cedula, String nombre) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setCedula(cedula);
        usuario.setNombre(nombre);
        usuario.setCorreo(CORREO);
        usuario.setIdRol(rolEstudiante);
        usuario.setCreadoEn(Instant.now());
        return usuario;
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("politica", "poliza.pdf", "application/pdf", "%PDF-1.4 poliza".getBytes());
    }
}
