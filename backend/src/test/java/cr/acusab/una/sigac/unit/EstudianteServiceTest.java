package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.CentrosEducativo;
import cr.acusab.una.sigac.data.entity.Encargado;
import cr.acusab.una.sigac.data.entity.EstadosUsuario;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.EstudiantesInstrumento;
import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.entity.Role;
import cr.acusab.una.sigac.data.entity.TiposDocumento;
import cr.acusab.una.sigac.data.entity.TiposIdentificacion;
import cr.acusab.una.sigac.data.entity.Usuario;

import cr.acusab.una.sigac.data.repository.CentroEducativoRepository;
import cr.acusab.una.sigac.data.repository.DocumentoEstudianteRepository;
import cr.acusab.una.sigac.data.repository.EncargadoRepository;
import cr.acusab.una.sigac.data.repository.EstadoUsuarioRepository;
import cr.acusab.una.sigac.data.repository.EstudianteEncargadoRepository;
import cr.acusab.una.sigac.data.repository.EstudianteInstrumentoRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.InstrumentoRepository;
import cr.acusab.una.sigac.data.repository.RoleRepository;
import cr.acusab.una.sigac.data.repository.TipoDocumentoRepository;
import cr.acusab.una.sigac.data.repository.TipoIdentificacionRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;

import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.exception.DuplicateResourceException;
import cr.acusab.una.sigac.logic.service.BecaService;
import cr.acusab.una.sigac.logic.service.EstudianteService;

import cr.acusab.una.sigac.presentation.dto.request.EstudianteCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteBusquedaResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstudianteServiceTest {

    @Mock
    private EstudianteRepository estudianteRepository;

    @Mock
    private EncargadoRepository encargadoRepository;

    @Mock
    private EstudianteEncargadoRepository estudianteEncargadoRepository;

    @Mock
    private InstrumentoRepository instrumentoRepository;

    @Mock
    private EstudianteInstrumentoRepository estudianteInstrumentoRepository;

    @Mock
    private DocumentoEstudianteRepository documentoEstudianteRepository;

    @Mock
    private TipoIdentificacionRepository tipoIdentificacionRepository;

    @Mock
    private TipoDocumentoRepository tipoDocumentoRepository;

    @Mock
    private CentroEducativoRepository centroEducativoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private EstadoUsuarioRepository estadoUsuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private BecaService becaService;

    @InjectMocks
    private EstudianteService estudianteService;

    @TempDir
    Path carpetaTemporal;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                estudianteService,
                "storageDirectory",
                carpetaTemporal.toString()
        );
    }

    /*
     * HU-01 - Registrar estudiante.
     * Caso normal: registrar correctamente un estudiante adulto
     * con información válida.
     */
    @Test
    void debeCrearEstudianteAdultoCorrectamente() {

        EstudianteCreateRequest request =
                crearRequestAdulto("ana@correo.com");

        MockMultipartFile poliza = crearPdfValido();

        configurarCreacionValida(request);

        EstudianteResponse resultado =
                estudianteService.crear(request, poliza);

        assertNotNull(resultado);

        ArgumentCaptor<Estudiante> captor =
                ArgumentCaptor.forClass(Estudiante.class);

        verify(estudianteRepository).save(captor.capture());

        Estudiante guardado = captor.getValue();

        assertEquals(
                "123456789",
                guardado.getNumeroIdentificacion()
        );

        assertEquals(
                "Ana",
                guardado.getNombres()
        );

        assertEquals(
                "Mora",
                guardado.getPrimerApellido()
        );

        assertTrue(guardado.getActivo());

        verify(estudianteInstrumentoRepository)
                .save(any(EstudiantesInstrumento.class));

        verify(documentoEstudianteRepository)
                .save(any());
    }

    /*
     * HU-01 - Registrar estudiante.
     * Caso normal: registrar correctamente un menor
     * junto con su encargado.
     */
    @Test
    void debeCrearEstudianteMenorConEncargadoCorrectamente() {

        EstudianteCreateRequest request =
                crearRequestMenorConEncargado();

        MockMultipartFile poliza = crearPdfValido();

        configurarCreacionValida(request);

        EstudianteResponse resultado =
                estudianteService.crear(request, poliza);

        assertNotNull(resultado);

        verify(encargadoRepository)
                .save(any(Encargado.class));

        verify(estudianteEncargadoRepository)
                .save(any());

        verify(estudianteRepository)
                .save(any(Estudiante.class));
    }

    /*
     * HU-01 - Registrar estudiante.
     * Caso de error: identificación duplicada.
     */
    @Test
    void noDebeCrearEstudianteConIdentificacionDuplicada() {

        EstudianteCreateRequest request =
                crearRequestAdulto("ana@correo.com");

        when(estudianteRepository
                .existsByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                        1,
                        "123456789"
                ))
                .thenReturn(true);

        assertThrows(
                DuplicateResourceException.class,
                () -> estudianteService.crear(
                        request,
                        crearPdfValido()
                )
        );

        verify(estudianteRepository, never())
                .save(any(Estudiante.class));
    }

    /*
     * HU-01 - Registrar estudiante.
     * Caso de error: género no permitido.
     */
    @Test
    void noDebeCrearEstudianteConGeneroInvalido() {

        EstudianteCreateRequest base =
                crearRequestAdulto("ana@correo.com");

        EstudianteCreateRequest request =
                new EstudianteCreateRequest(
                        base.idTipoIdentificacion(),
                        base.numeroIdentificacion(),
                        base.nombres(),
                        base.primerApellido(),
                        base.segundoApellido(),
                        base.fechaNacimiento(),
                        "INVALIDO",
                        base.direccion(),
                        base.telefono(),
                        base.correo(),
                        base.nivelEscolar(),
                        base.idCentroEducativo(),
                        base.condicionesRelevantes(),
                        base.idInstrumento(),
                        base.encargados(),
                        base.beca()
                );

        assertThrows(
                BusinessException.class,
                () -> estudianteService.crear(
                        request,
                        crearPdfValido()
                )
        );

        verify(estudianteRepository, never())
                .save(any(Estudiante.class));
    }

    /*
     * HU-01 - Registrar estudiante.
     * Caso de error: la póliza es obligatoria.
     */
    @Test
    void noDebeCrearEstudianteSinPoliza() {

        EstudianteCreateRequest request =
                crearRequestAdulto("ana@correo.com");

        assertThrows(
                BusinessException.class,
                () -> estudianteService.crear(
                        request,
                        null
                )
        );

        verify(estudianteRepository, never())
                .save(any(Estudiante.class));
    }

    /*
     * HU-01 - Registrar estudiante.
     * Caso de error: el archivo indica ser PDF,
     * pero su contenido no posee la cabecera PDF válida.
     */
    @Test
    void noDebeCrearEstudianteConPdfInvalido() {

        EstudianteCreateRequest request =
                crearRequestAdulto("ana@correo.com");

        MockMultipartFile archivoInvalido =
                new MockMultipartFile(
                        "politica",
                        "poliza.pdf",
                        "application/pdf",
                        "ESTO-NO-ES-PDF"
                                .getBytes(StandardCharsets.UTF_8)
                );

        assertThrows(
                BusinessException.class,
                () -> estudianteService.crear(
                        request,
                        archivoInvalido
                )
        );

        verify(estudianteRepository, never())
                .save(any(Estudiante.class));
    }

    /*
     * HU-01 - Registrar estudiante.
     * Caso límite: un menor debe tener al menos
     * un encargado.
     */
    @Test
    void noDebeCrearMenorSinEncargado() {

        EstudianteCreateRequest request =
                new EstudianteCreateRequest(
                        1,
                        "123456789",
                        "Luis",
                        "Mora",
                        "Soto",
                        LocalDate.now().minusYears(10),
                        "MASCULINO",
                        "Heredia",
                        "8888-1111",
                        "luis@correo.com",
                        "Primaria",
                        1L,
                        null,
                        5,
                        List.of(),
                        null
                );

        assertThrows(
                BusinessException.class,
                () -> estudianteService.crear(
                        request,
                        crearPdfValido()
                )
        );

        verify(estudianteRepository, never())
                .save(any(Estudiante.class));
    }

    /*
     * HU-01 - Registrar estudiante.
     * Caso límite: los encargados solamente corresponden
     * a estudiantes menores de edad.
     */
    @Test
    void noDebeCrearAdultoConEncargado() {

        EstudianteCreateRequest.EncargadoRequest encargado =
                crearEncargadoValido();

        EstudianteCreateRequest request =
                new EstudianteCreateRequest(
                        1,
                        "123456789",
                        "Ana",
                        "Mora",
                        "Soto",
                        LocalDate.now().minusYears(20),
                        "FEMENINO",
                        "Heredia",
                        "8888-1111",
                        "ana@correo.com",
                        "Universitario",
                        null,
                        null,
                        5,
                        List.of(encargado),
                        null
                );

        assertThrows(
                BusinessException.class,
                () -> estudianteService.crear(
                        request,
                        crearPdfValido()
                )
        );

        verify(estudianteRepository, never())
                .save(any(Estudiante.class));
    }

    /*
     * HU-01 - Registrar estudiante.
     * Caso límite: un estudiante entre 6 y 17 años
     * debe tener centro educativo.
     */
    @Test
    void noDebeCrearMenorSinCentroEducativo() {

        EstudianteCreateRequest request =
                new EstudianteCreateRequest(
                        1,
                        "123456789",
                        "Luis",
                        "Mora",
                        "Soto",
                        LocalDate.now().minusYears(10),
                        "MASCULINO",
                        "Heredia",
                        "8888-1111",
                        "luis@correo.com",
                        "Primaria",
                        null,
                        null,
                        5,
                        List.of(crearEncargadoValido()),
                        null
                );

        assertThrows(
                BusinessException.class,
                () -> estudianteService.crear(
                        request,
                        crearPdfValido()
                )
        );

        verify(estudianteRepository, never())
                .save(any(Estudiante.class));
    }

    /*
     * HU-01 - Registrar estudiante.
     * Caso de error: instrumento inexistente o inactivo.
     */
    @Test
    void noDebeCrearEstudianteConInstrumentoInexistente() {

        EstudianteCreateRequest request =
                crearRequestAdulto("ana@correo.com");

        when(instrumentoRepository
                .findByIdAndActivoTrue(5))
                .thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> estudianteService.crear(
                        request,
                        crearPdfValido()
                )
        );

        verify(estudianteRepository, never())
                .save(any(Estudiante.class));
    }

    /*
     * HU-01 - Registrar estudiante.
     * Caso de error: ya existe un usuario
     * con la misma identificación.
     */
    @Test
    void noDebeCrearEstudianteSiUsuarioYaExiste() {

        EstudianteCreateRequest request =
                crearRequestAdulto("ana@correo.com");

        Instrumento instrumento = mock(Instrumento.class);
        TiposIdentificacion tipo =
                mock(TiposIdentificacion.class);
        TiposDocumento tipoDocumento =
                mock(TiposDocumento.class);

        when(instrumentoRepository
                .findByIdAndActivoTrue(5))
                .thenReturn(Optional.of(instrumento));

        when(tipoIdentificacionRepository
                .findByIdAndActivoTrue(1))
                .thenReturn(Optional.of(tipo));

        when(tipoDocumentoRepository
                .findByCodigoAndActivoTrue("POLIZA"))
                .thenReturn(Optional.of(tipoDocumento));

        when(usuarioRepository.findByCedula("123456789"))
                .thenReturn(Optional.of(new Usuario()));

        assertThrows(
                DuplicateResourceException.class,
                () -> estudianteService.crear(
                        request,
                        crearPdfValido()
                )
        );

        verify(estudianteRepository, never())
                .save(any(Estudiante.class));
    }

    /*
     * HU-01 - Registrar estudiante.
     * Caso normal: si no se proporciona correo,
     * se genera un correo institucional.
     */
    @Test
    void debeGenerarCorreoInstitucionalSiCorreoEsNulo() {

        EstudianteCreateRequest request =
                crearRequestAdulto(null);

        configurarCreacionValida(request);

        estudianteService.crear(
                request,
                crearPdfValido()
        );

        ArgumentCaptor<Usuario> captor =
                ArgumentCaptor.forClass(Usuario.class);

        verify(usuarioRepository)
                .save(captor.capture());

        Usuario usuario = captor.getValue();

        assertEquals(
                "123456789@estudiante.sigac.local",
                usuario.getCorreo()
        );

        assertTrue(
                usuario.getRequiereCambioContrasena()
        );
    }

    /*
     * HU-01 - Buscar estudiante.
     * Caso límite: no ejecuta una búsqueda cuando
     * el texto tiene menos de dos caracteres.
     */
    @Test
    void debeRetornarListaVaciaSiBusquedaEsMuyCorta() {

        List<EstudianteBusquedaResponse> resultado =
                estudianteService.buscar("A");

        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());

        verify(estudianteRepository, never())
                .buscarPorNombreOIdentificacion(
                        anyString(),
                        any()
                );
    }

    /*
     * HU-01 - Buscar estudiante.
     * Caso normal: encuentra estudiantes por nombre
     * o identificación.
     */
    @Test
    void debeBuscarEstudiantesCorrectamente() {

        Estudiante estudiante =
                crearEstudianteExistente();

        when(estudianteRepository
                .buscarPorNombreOIdentificacion(
                        eq("Ana"),
                        any()
                ))
                .thenReturn(List.of(estudiante));

        List<EstudianteBusquedaResponse> resultado =
                estudianteService.buscar("Ana");

        assertNotNull(resultado);
        assertEquals(1, resultado.size());

        verify(estudianteRepository)
                .buscarPorNombreOIdentificacion(
                        eq("Ana"),
                        any()
                );
    }

    /*
     * HU-01 - Consultar estudiante.
     * Caso normal: estudiante existente.
     */
    @Test
    void debeObtenerEstudianteExistente() {

        Estudiante estudiante =
                crearEstudianteExistente();

        when(estudianteRepository.findById(100L))
                .thenReturn(Optional.of(estudiante));

        when(estudianteInstrumentoRepository.findAll())
                .thenReturn(List.of());

        EstudianteResponse resultado =
                estudianteService.obtener(100L);

        assertNotNull(resultado);

        verify(estudianteRepository)
                .findById(100L);
    }

    /*
     * HU-01 - Consultar estudiante.
     * Caso de error: estudiante inexistente.
     */
    @Test
    void noDebeObtenerEstudianteInexistente() {

        when(estudianteRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> estudianteService.obtener(999L)
        );

        verify(estudianteInstrumentoRepository, never())
                .findAll();
    }

    /*
     * HU-01 - Modificar estudiante.
     * Caso normal: modifica correctamente los datos
     * de un estudiante existente.
     */
    @Test
    void debeActualizarEstudianteCorrectamente() {

        Estudiante estudiante =
                crearEstudianteExistente();

        EstudianteUpdateRequest request =
                crearRequestActualizacion();

        Instrumento instrumento = mock(Instrumento.class);

        when(instrumento.getId()).thenReturn(6);

        TiposIdentificacion tipo =
                mock(TiposIdentificacion.class);

        when(estudianteRepository.findById(100L))
                .thenReturn(Optional.of(estudiante));

        when(estudianteRepository
                .findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                        1,
                        "123456789"
                ))
                .thenReturn(Optional.empty());

        when(instrumentoRepository
                .findByIdAndActivoTrue(6))
                .thenReturn(Optional.of(instrumento));

        when(tipoIdentificacionRepository
                .findByIdAndActivoTrue(1))
                .thenReturn(Optional.of(tipo));

        when(estudianteRepository
                .save(any(Estudiante.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(estudianteInstrumentoRepository.findAll())
                .thenReturn(List.of());

        EstudianteResponse resultado =
                estudianteService.actualizar(
                        100L,
                        request
                );

        assertNotNull(resultado);

        ArgumentCaptor<Estudiante> captor =
                ArgumentCaptor.forClass(Estudiante.class);

        verify(estudianteRepository)
                .save(captor.capture());

        Estudiante actualizado =
                captor.getValue();

        assertEquals(
                "Ana Maria",
                actualizado.getNombres()
        );

        assertEquals(
                "9999-9999",
                actualizado.getTelefono()
        );

        assertEquals(
                "ana.actualizada@correo.com",
                actualizado.getCorreo()
        );

        verify(estudianteInstrumentoRepository)
                .save(any(EstudiantesInstrumento.class));
    }

    /*
     * HU-01 - Modificar estudiante.
     * Caso de error: la identificación nueva ya pertenece
     * a otro estudiante.
     */
    @Test
    void noDebeActualizarConIdentificacionDuplicada() {

        Estudiante estudiante =
                crearEstudianteExistente();

        Estudiante otroEstudiante =
                crearEstudianteExistente();

        otroEstudiante.setId(200L);

        EstudianteUpdateRequest request =
                crearRequestActualizacion();

        when(estudianteRepository.findById(100L))
                .thenReturn(Optional.of(estudiante));

        when(estudianteRepository
                .findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                        1,
                        "123456789"
                ))
                .thenReturn(
                        Optional.of(otroEstudiante)
                );

        assertThrows(
                DuplicateResourceException.class,
                () -> estudianteService.actualizar(
                        100L,
                        request
                )
        );

        verify(estudianteRepository, never())
                .save(any(Estudiante.class));
    }

    /*
     * =========================================================
     * Métodos auxiliares
     * =========================================================
     */

    private EstudianteCreateRequest crearRequestAdulto(
            String correo
    ) {

        return new EstudianteCreateRequest(
                1,
                "123456789",
                "Ana",
                "Mora",
                "Soto",
                LocalDate.now().minusYears(20),
                "FEMENINO",
                "Heredia",
                "8888-1111",
                correo,
                "Universitario",
                null,
                null,
                5,
                List.of(),
                null
        );
    }

    private EstudianteCreateRequest crearRequestMenorConEncargado() {

        return new EstudianteCreateRequest(
                1,
                "123456789",
                "Luis",
                "Mora",
                "Soto",
                LocalDate.now().minusYears(10),
                "MASCULINO",
                "Heredia",
                "8888-1111",
                "luis@correo.com",
                "Primaria",
                1L,
                null,
                5,
                List.of(crearEncargadoValido()),
                null
        );
    }

    private EstudianteCreateRequest.EncargadoRequest
    crearEncargadoValido() {

        return new EstudianteCreateRequest.EncargadoRequest(
                null,
                null,
                "Maria",
                "Mora",
                "Lopez",
                "8888-2222",
                null,
                "maria@correo.com",
                "Docente",
                "MADRE",
                true,
                true
        );
    }

    private EstudianteUpdateRequest
    crearRequestActualizacion() {

        return new EstudianteUpdateRequest(
                1,
                "123456789",
                "Ana Maria",
                "Mora",
                "Soto",
                LocalDate.now().minusYears(20),
                "FEMENINO",
                "San Jose",
                "9999-9999",
                "ana.actualizada@correo.com",
                "Universitario",
                null,
                "Ninguna",
                6
        );
    }

    private MockMultipartFile crearPdfValido() {

        return new MockMultipartFile(
                "politica",
                "poliza.pdf",
                "application/pdf",
                "%PDF-1.4\nDocumento de prueba"
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    private void configurarCreacionValida(
            EstudianteCreateRequest request
    ) {

        Instrumento instrumento =
                mock(Instrumento.class);

        when(instrumento.getId())
                .thenReturn(request.idInstrumento());

        TiposIdentificacion tipo =
                mock(TiposIdentificacion.class);

        TiposDocumento tipoDocumento =
                mock(TiposDocumento.class);

        Role rol =
                mock(Role.class);

        EstadosUsuario estado =
                mock(EstadosUsuario.class);

        when(estado.getCodigo())
                .thenReturn("ACTIVO");

        when(estudianteRepository
                .existsByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                        request.idTipoIdentificacion(),
                        request.numeroIdentificacion().trim()
                ))
                .thenReturn(false);

        if (request.idCentroEducativo() != null) {

            CentrosEducativo centro =
                    mock(CentrosEducativo.class);

            when(centro.getActivo())
                    .thenReturn(true);

            when(centro.getId())
                    .thenReturn(
                            request.idCentroEducativo()
                    );

            when(centroEducativoRepository
                    .findById(
                            request.idCentroEducativo()
                    ))
                    .thenReturn(
                            Optional.of(centro)
                    );
        }

        when(instrumentoRepository
                .findByIdAndActivoTrue(
                        request.idInstrumento()
                ))
                .thenReturn(
                        Optional.of(instrumento)
                );

        when(tipoIdentificacionRepository
                .findByIdAndActivoTrue(
                        request.idTipoIdentificacion()
                ))
                .thenReturn(
                        Optional.of(tipo)
                );

        when(tipoDocumentoRepository
                .findByCodigoAndActivoTrue("POLIZA"))
                .thenReturn(
                        Optional.of(tipoDocumento)
                );

        when(usuarioRepository
                .findByCedula(
                        request.numeroIdentificacion().trim()
                ))
                .thenReturn(Optional.empty());

        if (request.correo() != null) {

            when(usuarioRepository
                    .findByCorreo(
                            request.correo().trim()
                    ))
                    .thenReturn(Optional.empty());
        }

        when(roleRepository
                .findByCodigoIgnoreCase("ESTUDIANTE"))
                .thenReturn(
                        Optional.of(rol)
                );

        when(estadoUsuarioRepository.findAll())
                .thenReturn(
                        List.of(estado)
                );

        when(passwordEncoder
                .encode("Temporal123"))
                .thenReturn("hash-temporal");

        when(usuarioRepository
                .save(any(Usuario.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        when(estudianteRepository
                .save(any(Estudiante.class)))
                .thenAnswer(invocation -> {

                    Estudiante estudiante =
                            invocation.getArgument(0);

                    estudiante.setId(100L);

                    return estudiante;
                });

        if (request.encargados() != null
                && !request.encargados().isEmpty()) {

            when(encargadoRepository
                    .save(any(Encargado.class)))
                    .thenAnswer(invocation ->
                            invocation.getArgument(0)
                    );
        }
    }

    private Estudiante crearEstudianteExistente() {

        Estudiante estudiante =
                new Estudiante();

        estudiante.setId(100L);
        estudiante.setNumeroIdentificacion(
                "123456789"
        );

        estudiante.setNombres("Ana");
        estudiante.setPrimerApellido("Mora");
        estudiante.setSegundoApellido("Soto");

        estudiante.setFechaNacimiento(
                LocalDate.now().minusYears(20)
        );

        estudiante.setGenero("FEMENINO");
        estudiante.setDireccion("Heredia");
        estudiante.setTelefono("8888-1111");
        estudiante.setCorreo("ana@correo.com");
        estudiante.setNivelEscolar("Universitario");

        estudiante.setIdCentroEducativo(null);
        estudiante.setCondicionesRelevantes(null);
        estudiante.setActivo(true);

        return estudiante;
    }
}