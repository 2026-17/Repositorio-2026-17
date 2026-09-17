package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.service.PeriodoAcademicoService;
import cr.acusab.una.sigac.presentation.dto.request.PeriodoAcademicoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.PeriodoAcademicoResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PeriodoAcademicoServiceTest {

    private PeriodoAcademicoRepository periodoAcademicoRepository;
    private UsuarioRepository usuarioRepository;

    private PeriodoAcademicoService periodoAcademicoService;

    @BeforeEach
    void setUp() {

        periodoAcademicoRepository = mock(PeriodoAcademicoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);

        periodoAcademicoService = new PeriodoAcademicoService(
                periodoAcademicoRepository,
                usuarioRepository
        );
    }

    @AfterEach
    void limpiarContextoSeguridad() {
        SecurityContextHolder.clearContext();
    }

    /*
     * ============================================================
     * Crear período académico
     * ============================================================
     */

    @Test
    void creaPeriodoCorrectamente() {

        PeriodoAcademicoRequestDTO request = crearRequestValido();

        when(periodoAcademicoRepository.existsByNombre("I Cuatrimestre 2027"))
                .thenReturn(false);

        when(periodoAcademicoRepository.existsByCodigo(anyString()))
                .thenReturn(false);

        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(7L);

        when(usuarioRepository.findByCedula("123456789"))
                .thenReturn(Optional.of(usuario));

        configurarUsuarioAutenticado();

        when(periodoAcademicoRepository.save(any(PeriodoAcademico.class)))
                .thenAnswer(invocation -> {
                    PeriodoAcademico periodo = invocation.getArgument(0);
                    periodo.setId(50L);
                    return periodo;
                });

        PeriodoAcademicoResponse respuesta =
                periodoAcademicoService.crear(request);

        assertNotNull(respuesta);
        assertEquals(50L, respuesta.getId());
        assertEquals("I Cuatrimestre 2027", respuesta.getNombre());
        assertEquals(LocalDate.of(2027, 1, 10), respuesta.getFechaInicio());
        assertEquals(LocalDate.of(2027, 4, 30), respuesta.getFechaFin());
        assertEquals("PLANIFICADO", respuesta.getEstado());
        assertNotNull(respuesta.getCodigo());

        ArgumentCaptor<PeriodoAcademico> captor =
                ArgumentCaptor.forClass(PeriodoAcademico.class);

        verify(periodoAcademicoRepository).save(captor.capture());

        PeriodoAcademico periodoCreado = captor.getValue();

        assertEquals("I Cuatrimestre 2027", periodoCreado.getNombre());
        assertEquals(7L, periodoCreado.getIdUsuarioUltimaModificacion());
        assertNotNull(periodoCreado.getCreadoEn());
        assertNotNull(periodoCreado.getActualizadoEn());
    }

    @Test
    void noDebeCrearPeriodoSiFechaFinNoEsPosteriorAFechaInicio() {

        PeriodoAcademicoRequestDTO request = crearRequestValido();
        request.setFechaFin(request.getFechaInicio());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> periodoAcademicoService.crear(request)
                );

        assertEquals(
                "La fecha de fin debe ser posterior a la fecha de inicio.",
                exception.getMessage()
        );

        verify(periodoAcademicoRepository, never())
                .save(any(PeriodoAcademico.class));
    }

    @Test
    void noDebeCrearPeriodoSiFechaFinMatriculaEsAnteriorAFechaInicioMatricula() {

        PeriodoAcademicoRequestDTO request = crearRequestValido();
        request.setFechaInicioMatricula(LocalDate.of(2026, 12, 20));
        request.setFechaFinMatricula(LocalDate.of(2026, 12, 10));

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> periodoAcademicoService.crear(request)
                );

        assertEquals(
                "La fecha de fin de matrícula no puede ser anterior "
                        + "a la fecha de inicio de matrícula.",
                exception.getMessage()
        );

        verify(periodoAcademicoRepository, never())
                .save(any(PeriodoAcademico.class));
    }

    @Test
    void noDebeCrearPeriodoConEstadoInvalido() {

        PeriodoAcademicoRequestDTO request = crearRequestValido();
        request.setEstado("INVENTADO");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> periodoAcademicoService.crear(request)
                );

        assertEquals(
                "El estado indicado no es válido.",
                exception.getMessage()
        );

        verify(periodoAcademicoRepository, never())
                .save(any(PeriodoAcademico.class));
    }

    @Test
    void noDebeCrearPeriodoConNombreDuplicado() {

        PeriodoAcademicoRequestDTO request = crearRequestValido();

        when(periodoAcademicoRepository.existsByNombre("I Cuatrimestre 2027"))
                .thenReturn(true);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> periodoAcademicoService.crear(request)
                );

        assertEquals(
                "Ya existe un período académico con el nombre ingresado.",
                exception.getMessage()
        );

        verify(periodoAcademicoRepository, never())
                .save(any(PeriodoAcademico.class));
    }

    /*
     * ============================================================
     * Listado de períodos disponibles
     * ============================================================
     */

    @Test
    void debeListarPeriodosDisponibles() {

        when(periodoAcademicoRepository.findByEstadoInOrderByFechaInicioDesc(
                List.of("PLANIFICADO", "MATRICULA", "EN_CURSO")
        )).thenReturn(List.of());

        List<PeriodoAcademicoResponse> resultado =
                periodoAcademicoService.listarPeriodosDisponibles();

        assertTrue(resultado.isEmpty());
    }

    /*
     * ============================================================
     * Modificación de período académico
     * ============================================================
     */

    @Test
    void actualizaPeriodoCorrectamente() {

        PeriodoAcademico periodoExistente = new PeriodoAcademico();
        periodoExistente.setId(50L);
        periodoExistente.setCodigo("I-CUATRIMESTRE-2027");
        periodoExistente.setNombre("I Cuatrimestre 2027");
        periodoExistente.setFechaInicio(LocalDate.of(2027, 1, 10));
        periodoExistente.setFechaFin(LocalDate.of(2027, 4, 30));
        periodoExistente.setEstado("PLANIFICADO");

        when(periodoAcademicoRepository.findById(50L))
                .thenReturn(Optional.of(periodoExistente));

        PeriodoAcademicoRequestDTO request = crearRequestValido();
        request.setNombre("I Cuatrimestre 2027 (ajustado)");
        request.setEstado("MATRICULA");

        when(periodoAcademicoRepository.existsByNombreAndIdNot(
                "I Cuatrimestre 2027 (ajustado)", 50L
        )).thenReturn(false);

        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(7L);

        when(usuarioRepository.findByCedula("123456789"))
                .thenReturn(Optional.of(usuario));

        configurarUsuarioAutenticado();

        when(periodoAcademicoRepository.save(any(PeriodoAcademico.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PeriodoAcademicoResponse respuesta =
                periodoAcademicoService.actualizar(50L, request);

        assertEquals("I Cuatrimestre 2027 (ajustado)", respuesta.getNombre());
        assertEquals("MATRICULA", respuesta.getEstado());

        // El código no debe regenerarse al modificar.
        assertEquals("I-CUATRIMESTRE-2027", respuesta.getCodigo());

        assertEquals(
                7L,
                periodoExistente.getIdUsuarioUltimaModificacion()
        );
    }

    @Test
    void noDebeActualizarPeriodoInexistente() {

        when(periodoAcademicoRepository.findById(999L))
                .thenReturn(Optional.empty());

        PeriodoAcademicoRequestDTO request = crearRequestValido();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> periodoAcademicoService.actualizar(999L, request)
                );

        assertEquals(
                "El período académico indicado no existe.",
                exception.getMessage()
        );

        verify(periodoAcademicoRepository, never())
                .save(any(PeriodoAcademico.class));
    }

    @Test
    void noDebeActualizarPeriodoConNombreDuplicadoDeOtroPeriodo() {

        PeriodoAcademico periodoExistente = new PeriodoAcademico();
        periodoExistente.setId(50L);
        periodoExistente.setNombre("I Cuatrimestre 2027");

        when(periodoAcademicoRepository.findById(50L))
                .thenReturn(Optional.of(periodoExistente));

        PeriodoAcademicoRequestDTO request = crearRequestValido();

        when(periodoAcademicoRepository.existsByNombreAndIdNot(
                "I Cuatrimestre 2027", 50L
        )).thenReturn(true);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> periodoAcademicoService.actualizar(50L, request)
                );

        assertEquals(
                "Ya existe un período académico con el nombre ingresado.",
                exception.getMessage()
        );

        verify(periodoAcademicoRepository, never())
                .save(any(PeriodoAcademico.class));
    }

    /*
     * ============================================================
     * Listado completo y consulta de un período
     * ============================================================
     */

    @Test
    void debeListarTodosLosPeriodos() {

        when(periodoAcademicoRepository.findAll())
                .thenReturn(List.of());

        List<PeriodoAcademicoResponse> resultado =
                periodoAcademicoService.listarTodos();

        assertTrue(resultado.isEmpty());
    }

    @Test
    void noDebeObtenerPeriodoInexistente() {

        when(periodoAcademicoRepository.findById(999L))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> periodoAcademicoService.obtener(999L)
                );

        assertEquals(
                "El período académico indicado no existe.",
                exception.getMessage()
        );
    }

    /*
     * ============================================================
     * Métodos auxiliares para las pruebas
     * ============================================================
     */

    private PeriodoAcademicoRequestDTO crearRequestValido() {

        PeriodoAcademicoRequestDTO request = new PeriodoAcademicoRequestDTO();

        request.setNombre("I Cuatrimestre 2027");
        request.setFechaInicio(LocalDate.of(2027, 1, 10));
        request.setFechaFin(LocalDate.of(2027, 4, 30));
        request.setFechaInicioMatricula(LocalDate.of(2026, 12, 1));
        request.setFechaFinMatricula(LocalDate.of(2026, 12, 15));
        request.setEstado("PLANIFICADO");

        return request;
    }

    private void configurarUsuarioAutenticado() {

        Authentication authentication = mock(Authentication.class);

        when(authentication.isAuthenticated())
                .thenReturn(true);

        when(authentication.getName())
                .thenReturn("123456789");

        SecurityContext securityContext = mock(SecurityContext.class);

        when(securityContext.getAuthentication())
                .thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);
    }
}
