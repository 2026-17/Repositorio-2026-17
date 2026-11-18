package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Canton;
import cr.acusab.una.sigac.data.entity.Distrito;
import cr.acusab.una.sigac.data.entity.EstadosUsuario;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.entity.Provincia;
import cr.acusab.una.sigac.data.entity.Role;
import cr.acusab.una.sigac.data.entity.TiposDocumento;
import cr.acusab.una.sigac.data.entity.TiposEstudiante;
import cr.acusab.una.sigac.data.entity.TiposIdentificacion;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.exception.DuplicateResourceException;
import cr.acusab.una.sigac.logic.service.BecaService;
import cr.acusab.una.sigac.logic.service.ContrasenaTemporalService;
import cr.acusab.una.sigac.logic.service.CuentaUsuarioService;
import cr.acusab.una.sigac.logic.service.EstudianteService;
import cr.acusab.una.sigac.logic.service.InscripcionPlanEstudioService;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.response.BecaResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * Reglas de registro y edición de estudiantes: encargados, edad permitida
 * y sincronización de la cuenta de usuario.
 */
class EstudianteServiceTest {

    private static final String CEDULA_ESTUDIANTE = "118760542";

    private EstudianteRepository estudianteRepository;
    private TipoIdentificacionRepository tipoIdentificacionRepository;
    private UsuarioRepository usuarioRepository;
    private EstudianteEncargadoRepository estudianteEncargadoRepository;
    private InstrumentoRepository instrumentoRepository;
    private EstudianteInstrumentoRepository estudianteInstrumentoRepository;
    private DistritoRepository distritoRepository;
    private InscripcionPlanEstudioService inscripcionPlanService;
    private TipoDocumentoRepository tipoDocumentoRepository;
    private RoleRepository roleRepository;
    private EstadoUsuarioRepository estadoUsuarioRepository;
    private BecaService becaService;
    private TiposEstudianteRepository tiposEstudianteRepository;

    private EstudianteService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        estudianteRepository = mock(EstudianteRepository.class);
        tipoIdentificacionRepository = mock(TipoIdentificacionRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        estudianteEncargadoRepository = mock(EstudianteEncargadoRepository.class);
        instrumentoRepository = mock(InstrumentoRepository.class);
        estudianteInstrumentoRepository = mock(EstudianteInstrumentoRepository.class);
        distritoRepository = mock(DistritoRepository.class);
        inscripcionPlanService = mock(InscripcionPlanEstudioService.class);
        tipoDocumentoRepository = mock(TipoDocumentoRepository.class);
        roleRepository = mock(RoleRepository.class);
        estadoUsuarioRepository = mock(EstadoUsuarioRepository.class);
        becaService = mock(BecaService.class);
        tiposEstudianteRepository = mock(TiposEstudianteRepository.class);

        // Servicio de cuenta real: así se prueba la sincronización completa
        CuentaUsuarioService cuentaUsuarioService =
                new CuentaUsuarioService(usuarioRepository, mock(EstadoUsuarioRepository.class));

        service = new EstudianteService(
                estudianteRepository,
                mock(EncargadoRepository.class),
                estudianteEncargadoRepository,
                instrumentoRepository,
                estudianteInstrumentoRepository,
                mock(ProfesorRepository.class),
                mock(DocumentoEstudianteRepository.class),
                tipoIdentificacionRepository,
                tipoDocumentoRepository,
                mock(CentroEducativoRepository.class),
                usuarioRepository,
                roleRepository,
                estadoUsuarioRepository,
                mock(ContrasenaTemporalService.class),
                becaService,
                inscripcionPlanService,
                distritoRepository,
                cuentaUsuarioService,
                tiposEstudianteRepository
        );
        ReflectionTestUtils.setField(service, "storageDirectory", tempDir.toString());

        TiposIdentificacion cedula = new TiposIdentificacion();
        cedula.setId(1);
        cedula.setCodigo("CEDULA");
        cedula.setNombre("Cédula nacional");
        cedula.setActivo(true);
        when(tipoIdentificacionRepository.findByIdAndActivoTrue(1)).thenReturn(Optional.of(cedula));

        when(tiposEstudianteRepository.findByCodigo("REGULAR")).thenReturn(Optional.of(REGULAR));
        when(tiposEstudianteRepository.findByIdAndActivoTrue(1)).thenReturn(Optional.of(REGULAR));
        when(tiposEstudianteRepository.findByIdAndActivoTrue(2)).thenReturn(Optional.of(INCLUSION));
    }

    private static final TiposEstudiante REGULAR = tipoEstudiante(1, "REGULAR", "Regular");
    private static final TiposEstudiante INCLUSION = tipoEstudiante(2, "INCLUSION", "Inclusión");

    // ---------- Tipo de estudiante: registro ----------

    @Test
    void crear_sinTipoSeRegistraComoRegular() {
        prepararRegistro();

        EstudianteResponse respuesta = service.crear(registroAdulto(null, null), poliza());

        ArgumentCaptor<Estudiante> captor = ArgumentCaptor.forClass(Estudiante.class);
        verify(estudianteRepository).save(captor.capture());
        assertEquals("REGULAR", captor.getValue().getIdTipoEstudiante().getCodigo());
        assertEquals("REGULAR", respuesta.tipoEstudiante().codigo());
        assertNull(respuesta.beca());
        verify(becaService, never()).asignarBecaPermanenteInclusion(any());
        verify(becaService, never()).asignar(any());
    }

    @Test
    void crear_inclusionCreaLaBecaPermanente() {
        prepararRegistro();
        BecaResponse becaPermanente = new BecaResponse();
        becaPermanente.setPorcentaje(BigDecimal.valueOf(100));
        becaPermanente.setPermanente(true);
        when(becaService.asignarBecaPermanenteInclusion(any(Estudiante.class))).thenReturn(becaPermanente);

        EstudianteResponse respuesta = service.crear(registroAdulto(2, null), poliza());

        ArgumentCaptor<Estudiante> captor = ArgumentCaptor.forClass(Estudiante.class);
        verify(becaService).asignarBecaPermanenteInclusion(captor.capture());
        assertEquals(40L, captor.getValue().getId());
        assertEquals("INCLUSION", captor.getValue().getIdTipoEstudiante().getCodigo());
        assertEquals("INCLUSION", respuesta.tipoEstudiante().codigo());
        assertTrue(respuesta.beca().isPermanente());
        verify(becaService, never()).asignar(any());
    }

    @Test
    void crear_rechazaInclusionConBecaEnviada() {
        var beca = new EstudianteCreateRequest.BecaRequest(2L, 3, BigDecimal.valueOf(50));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.crear(registroAdulto(2, beca), poliza()));

        assertEquals("Los estudiantes de inclusión ya tienen beca del 100%", ex.getMessage());
        verify(usuarioRepository, never()).save(any());
        verify(estudianteRepository, never()).save(any());
        verify(becaService, never()).asignarBecaPermanenteInclusion(any());
    }

    @Test
    void crear_rechazaTipoDeEstudianteInexistente() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.crear(registroAdulto(99, null), poliza()));

        assertEquals("El tipo de estudiante indicado no existe o está inactivo", ex.getMessage());
        verify(estudianteRepository, never()).save(any());
    }

    // ---------- Tipo de estudiante: edición ----------

    @Test
    void actualizar_aRegularCierraLaBecaPermanente() {
        Usuario cuenta = new Usuario();
        cuenta.setId(30L);
        Estudiante estudiante = prepararEdicion(cuenta);
        estudiante.setIdTipoEstudiante(INCLUSION);

        EstudianteResponse respuesta = service.actualizar(5L, edicion(CEDULA_ESTUDIANTE, "ana@correo.com", 1));

        assertEquals("REGULAR", estudiante.getIdTipoEstudiante().getCodigo());
        assertEquals("REGULAR", respuesta.tipoEstudiante().codigo());
        verify(becaService).cerrarBecaPermanenteInclusion(5L);
        verify(becaService, never()).asignarBecaPermanenteInclusion(any());
    }

    @Test
    void actualizar_aInclusionCreaLaBecaPermanente() {
        Usuario cuenta = new Usuario();
        cuenta.setId(30L);
        Estudiante estudiante = prepararEdicion(cuenta);
        estudiante.setIdTipoEstudiante(REGULAR);

        service.actualizar(5L, edicion(CEDULA_ESTUDIANTE, "ana@correo.com", 2));

        assertEquals("INCLUSION", estudiante.getIdTipoEstudiante().getCodigo());
        verify(becaService).asignarBecaPermanenteInclusion(estudiante);
        verify(becaService, never()).cerrarBecaPermanenteInclusion(anyLong());
    }

    @Test
    void actualizar_sinTipoNoCambiaNiTocaLaBeca() {
        Usuario cuenta = new Usuario();
        cuenta.setId(30L);
        Estudiante estudiante = prepararEdicion(cuenta);
        estudiante.setIdTipoEstudiante(INCLUSION);

        service.actualizar(5L, edicion(CEDULA_ESTUDIANTE, "ana@correo.com", null));

        assertEquals("INCLUSION", estudiante.getIdTipoEstudiante().getCodigo());
        verify(becaService, never()).cerrarBecaPermanenteInclusion(anyLong());
        verify(becaService, never()).asignarBecaPermanenteInclusion(any());
    }

    // ---------- Tipo de estudiante: listado ----------

    @Test
    void listado_filtraPorTipoDeEstudiante() {
        Estudiante regular = estudianteListado(1L, "Ana", REGULAR);
        Estudiante inclusion = estudianteListado(2L, "Beto", INCLUSION);
        when(estudianteRepository.findAll()).thenReturn(List.of(regular, inclusion));

        var resultado = service.listado(null, null, 2);

        assertEquals(1, resultado.size());
        assertEquals(2L, resultado.get(0).id());
        assertEquals("INCLUSION", resultado.get(0).tipoEstudiante().codigo());
        assertEquals("Inclusión", resultado.get(0).tipoEstudiante().nombre());
    }

    // ---------- Registro: encargados ----------

    @Test
    void crear_rechazaEncargadoConLaMismaIdentificacionQueElEstudiante() {
        var request = registroMenor(List.of(encargado(CEDULA_ESTUDIANTE, true)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.crear(request, poliza()));

        assertEquals("El encargado no puede tener la misma identificación que el estudiante.", ex.getMessage());
    }

    @Test
    void crear_rechazaDosEncargadosConLaMismaIdentificacion() {
        var request = registroMenor(List.of(
                encargado("204440555", true),
                encargado("204440555", false)
        ));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.crear(request, poliza()));

        assertEquals("Los dos encargados no pueden tener la misma identificación.", ex.getMessage());
    }

    // ---------- Registro: edad ----------

    @Test
    void crear_rechazaEstudianteDeUnAnio() {
        var request = registro(LocalDate.now().minusYears(1), List.of(encargado("204440555", true)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.crear(request, poliza()));

        assertEquals("El estudiante debe tener al menos 2 años.", ex.getMessage());
    }

    // ---------- Edición: cuenta de usuario ----------

    @Test
    void actualizar_sincronizaCedulaYCorreoDeLaCuentaDeUsuario() {
        Usuario cuenta = new Usuario();
        cuenta.setId(30L);
        cuenta.setCedula(CEDULA_ESTUDIANTE);
        cuenta.setCorreo("viejo@correo.com");
        prepararEdicion(cuenta);

        service.actualizar(5L, edicion("118760543", "nuevo@correo.com"));

        assertEquals("118760543", cuenta.getCedula());
        assertEquals("nuevo@correo.com", cuenta.getCorreo());
        verify(usuarioRepository).save(cuenta);
    }

    @Test
    void actualizar_rechazaCorreoQueYaUsaOtraCuenta() {
        Usuario cuenta = new Usuario();
        cuenta.setId(30L);
        cuenta.setCedula(CEDULA_ESTUDIANTE);
        cuenta.setCorreo("viejo@correo.com");
        prepararEdicion(cuenta);

        Usuario otra = new Usuario();
        otra.setId(99L);
        when(usuarioRepository.findByCorreo("ocupado@correo.com")).thenReturn(Optional.of(otra));

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> service.actualizar(5L, edicion(CEDULA_ESTUDIANTE, "ocupado@correo.com")));

        assertEquals("El correo ya está registrado en otra cuenta.", ex.getMessage());
        verify(estudianteRepository, never()).save(any());
    }

    @Test
    void actualizar_rechazaMenorSinEncargadosRegistrados() {
        Usuario cuenta = new Usuario();
        cuenta.setId(30L);
        prepararEdicion(cuenta);
        when(estudianteEncargadoRepository.countByIdEstudiante(5L)).thenReturn(0L);

        var request = new EstudianteUpdateRequest(1, CEDULA_ESTUDIANTE, "Ana", "Mora", null,
                LocalDate.now().minusYears(10), "FEMENINO", 10101, "Barrio Centro", null,
                "ana@correo.com", "Primaria", "Escuela Central", null, 3, List.of(1L), null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.actualizar(5L, request));

        assertEquals("Un menor de edad debe tener al menos un encargado registrado.", ex.getMessage());
    }

    // ---------- Datos de prueba ----------

    private EstudianteCreateRequest registroMenor(List<EstudianteCreateRequest.EncargadoRequest> encargados) {
        return registro(LocalDate.now().minusYears(10), encargados);
    }

    private EstudianteCreateRequest registro(LocalDate nacimiento, List<EstudianteCreateRequest.EncargadoRequest> encargados) {
        return new EstudianteCreateRequest(1, CEDULA_ESTUDIANTE, "Ana", "Mora", null, nacimiento,
                "FEMENINO", 10101, "Barrio Centro", null, "ana@correo.com", "Primaria", "Escuela Central",
                null, 3, null, List.of(1L), encargados, null, null);
    }

    // Mayor de edad: no requiere encargados ni centro educativo
    private EstudianteCreateRequest registroAdulto(Integer idTipoEstudiante, EstudianteCreateRequest.BecaRequest beca) {
        return new EstudianteCreateRequest(1, CEDULA_ESTUDIANTE, "Ana", "Mora", null, LocalDate.now().minusYears(25),
                "FEMENINO", 10101, "Barrio Centro", null, "ana@correo.com", "Universitaria", null,
                null, 3, null, List.of(1L), List.of(), beca, idTipoEstudiante);
    }

    // Lo necesario para que un registro completo llegue hasta el final
    private void prepararRegistro() {
        Instrumento instrumento = new Instrumento();
        instrumento.setId(3);
        instrumento.setNombre("Piano");
        when(instrumentoRepository.findByIdAndActivoTrue(3)).thenReturn(Optional.of(instrumento));

        TiposDocumento poliza = new TiposDocumento();
        poliza.setId(1);
        poliza.setCodigo("POLIZA");
        when(tipoDocumentoRepository.findByCodigoAndActivoTrue("POLIZA")).thenReturn(Optional.of(poliza));

        when(distritoRepository.findById(10101)).thenReturn(Optional.of(distrito()));

        Role rol = new Role();
        rol.setCodigo("ESTUDIANTE");
        when(roleRepository.findByCodigoIgnoreCase("ESTUDIANTE")).thenReturn(Optional.of(rol));
        EstadosUsuario activo = new EstadosUsuario();
        activo.setCodigo("ACTIVO");
        when(estadoUsuarioRepository.findAll()).thenReturn(List.of(activo));

        when(usuarioRepository.findByCedula(any())).thenReturn(Optional.empty());
        when(usuarioRepository.findByCorreo(any())).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(invocation -> {
            Estudiante e = invocation.getArgument(0);
            e.setId(40L);
            return e;
        });
        when(inscripcionPlanService.sincronizar(anyLong(), any())).thenReturn(List.of(1L));
    }

    private Estudiante estudianteListado(Long id, String nombre, TiposEstudiante tipo) {
        Estudiante e = new Estudiante();
        e.setId(id);
        e.setNombres(nombre);
        e.setPrimerApellido("Mora");
        e.setNumeroIdentificacion("11876054" + id);
        e.setFechaNacimiento(LocalDate.now().minusYears(20));
        e.setActivo(true);
        e.setIdTipoEstudiante(tipo);
        return e;
    }

    private static TiposEstudiante tipoEstudiante(int id, String codigo, String nombre) {
        TiposEstudiante tipo = new TiposEstudiante();
        tipo.setId(id);
        tipo.setCodigo(codigo);
        tipo.setNombre(nombre);
        tipo.setActivo(true);
        return tipo;
    }

    private Distrito distrito() {
        Provincia provincia = new Provincia();
        provincia.setId(1);
        Canton canton = new Canton();
        canton.setCodigo(101);
        canton.setProvincia(provincia);
        Distrito distrito = new Distrito();
        distrito.setCodigo(10101);
        distrito.setCanton(canton);
        return distrito;
    }

    private EstudianteCreateRequest.EncargadoRequest encargado(String cedula, boolean principal) {
        return new EstudianteCreateRequest.EncargadoRequest(1, cedula, "Laura", "Mora", null,
                "88884321", null, null, null, "MADRE", principal, false);
    }

    private MockMultipartFile poliza() {
        return new MockMultipartFile("politica", "poliza.pdf", "application/pdf", "%PDF-1.4 poliza".getBytes());
    }

    private EstudianteUpdateRequest edicion(String cedula, String correo) {
        return edicion(cedula, correo, null);
    }

    private EstudianteUpdateRequest edicion(String cedula, String correo, Integer idTipoEstudiante) {
        // Mayor de edad: no exige encargados ni centro educativo
        return new EstudianteUpdateRequest(1, cedula, "Ana", "Mora", null, LocalDate.now().minusYears(25),
                "FEMENINO", 10101, "Barrio Centro", null, correo, "Universitaria", null, null, 3, List.of(1L),
                idTipoEstudiante);
    }

    private Estudiante prepararEdicion(Usuario cuenta) {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(5L);
        estudiante.setUsuario(cuenta);
        when(estudianteRepository.findById(5L)).thenReturn(Optional.of(estudiante));
        when(estudianteRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(any(), any()))
                .thenReturn(Optional.empty());

        Instrumento instrumento = new Instrumento();
        instrumento.setId(3);
        when(instrumentoRepository.findByIdAndActivoTrue(3)).thenReturn(Optional.of(instrumento));

        Provincia provincia = new Provincia();
        provincia.setId(1);
        Canton canton = new Canton();
        canton.setCodigo(101);
        canton.setProvincia(provincia);
        Distrito distrito = new Distrito();
        distrito.setCodigo(10101);
        distrito.setCanton(canton);
        when(distritoRepository.findById(10101)).thenReturn(Optional.of(distrito));

        when(usuarioRepository.findByCedula(any())).thenReturn(Optional.empty());
        when(usuarioRepository.findByCorreo(any())).thenReturn(Optional.empty());
        when(estudianteInstrumentoRepository.findAll()).thenReturn(List.of());
        when(inscripcionPlanService.sincronizar(anyLong(), any())).thenReturn(List.of(1L));
        return estudiante;
    }
}
