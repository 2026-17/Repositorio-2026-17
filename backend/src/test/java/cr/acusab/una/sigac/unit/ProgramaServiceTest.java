package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.PlanEstudio;
import cr.acusab.una.sigac.data.entity.Programa;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.MallaCurricularRepository;
import cr.acusab.una.sigac.data.repository.PlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.ProgramaRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.DuplicateResourceException;
import cr.acusab.una.sigac.logic.service.ProgramaService;
import cr.acusab.una.sigac.presentation.dto.request.PlanEstudioInicialRequest;
import cr.acusab.una.sigac.presentation.dto.request.ProgramaCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.ProgramaUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.response.ProgramaDetalleResponse;
import cr.acusab.una.sigac.presentation.dto.response.ProgramaDisponibilidadResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProgramaServiceTest {

    private ProgramaRepository programaRepository;
    private PlanEstudioRepository planEstudioRepository;
    private MallaCurricularRepository mallaCurricularRepository;
    private UsuarioRepository usuarioRepository;

    private ProgramaService programaService;

    @BeforeEach
    void setUp() {
        programaRepository = mock(ProgramaRepository.class);
        planEstudioRepository = mock(PlanEstudioRepository.class);
        mallaCurricularRepository = mock(MallaCurricularRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);

        programaService = new ProgramaService(
                programaRepository,
                planEstudioRepository,
                mallaCurricularRepository,
                usuarioRepository
        );

        // Por defecto, guardar devuelve la misma entidad con un id.
        when(programaRepository.save(any(Programa.class))).thenAnswer(inv -> {
            Programa p = inv.getArgument(0);
            if (p.getId() == null) p.setId(10L);
            return p;
        });
        when(planEstudioRepository.save(any(PlanEstudio.class))).thenAnswer(inv -> {
            PlanEstudio p = inv.getArgument(0);
            p.setId(20L);
            return p;
        });
    }

    @AfterEach
    void limpiarContextoSeguridad() {
        SecurityContextHolder.clearContext();
    }

    /* ============================================================
     * Crear programa
     * ============================================================ */

    @Test
    void creaProgramaSinPlanNormalizandoCodigoYNombre() {
        configurarUsuarioAutenticado();

        ProgramaCreateRequest request = crearRequest("  fmi-2027 ", "  Formación   Musical Infantil ");
        request.setDescripcion("   ");

        ProgramaDetalleResponse respuesta = programaService.crear(request);

        ArgumentCaptor<Programa> captor = ArgumentCaptor.forClass(Programa.class);
        verify(programaRepository).save(captor.capture());
        Programa guardado = captor.getValue();

        assertEquals("FMI-2027", guardado.getCodigo());
        assertEquals("Formación Musical Infantil", guardado.getNombre());
        assertNull(guardado.getDescripcion());
        assertTrue(guardado.getActivo());
        assertEquals(7L, guardado.getIdUsuarioUltimaModificacion());
        assertNotNull(guardado.getCreadoEn());

        verify(planEstudioRepository, never()).save(any());
        assertNull(respuesta.getPlanVigente());
    }

    @Test
    void creaProgramaConSuPlanInicialVigente() {
        configurarUsuarioAutenticado();

        ProgramaCreateRequest request = crearRequest("FMI", "Formación Musical Infantil");
        request.setPlanInicial(crearPlan("1.0"));

        programaService.crear(request);

        ArgumentCaptor<PlanEstudio> captor = ArgumentCaptor.forClass(PlanEstudio.class);
        verify(planEstudioRepository).save(captor.capture());
        PlanEstudio plan = captor.getValue();

        assertEquals(10L, plan.getIdPrograma());
        assertEquals("FMI-V1.0", plan.getCodigo());
        assertEquals("Plan de estudios Formación Musical Infantil v1.0", plan.getNombre());
        assertEquals("1.0", plan.getVersion());
        assertEquals(new BigDecimal("70.00"), plan.getNotaMinimaAprobacion());
        assertTrue(plan.getVigente());
        assertEquals(7L, plan.getIdUsuarioUltimaModificacion());
    }

    @Test
    void codigoDePlanRepetidoRecibeSufijo() {
        configurarUsuarioAutenticado();
        when(planEstudioRepository.existsByCodigo("FMI-V1.0")).thenReturn(true);

        ProgramaCreateRequest request = crearRequest("FMI", "Formación Musical Infantil");
        request.setPlanInicial(crearPlan("1.0"));

        programaService.crear(request);

        ArgumentCaptor<PlanEstudio> captor = ArgumentCaptor.forClass(PlanEstudio.class);
        verify(planEstudioRepository).save(captor.capture());
        assertEquals("FMI-V1.0-2", captor.getValue().getCodigo());
    }

    @Test
    void rechazaCodigoDuplicado() {
        when(programaRepository.existsByCodigoIgnoreCase("FMI")).thenReturn(true);

        DuplicateResourceException ex = assertThrows(
                DuplicateResourceException.class,
                () -> programaService.crear(crearRequest("fmi", "Otro nombre"))
        );

        assertTrue(ex.getMessage().contains("FMI"));
        verify(programaRepository, never()).save(any());
    }

    @Test
    void rechazaNombreDuplicado() {
        when(programaRepository.existsByNombreIgnoreCase("Formación Musical Infantil")).thenReturn(true);

        assertThrows(
                DuplicateResourceException.class,
                () -> programaService.crear(crearRequest("FMI", "Formación Musical Infantil"))
        );

        verify(programaRepository, never()).save(any());
    }

    /* ============================================================
     * Disponibilidad
     * ============================================================ */

    @Test
    void informaDisponibilidadDeCodigoYNombre() {
        when(programaRepository.existsByCodigoIgnoreCase("FMI")).thenReturn(true);
        when(programaRepository.existsByNombreIgnoreCase("Nuevo")).thenReturn(false);

        ProgramaDisponibilidadResponse r = programaService.consultarDisponibilidad(" fmi ", "Nuevo");

        assertFalse(r.isCodigoDisponible());
        assertTrue(r.isNombreDisponible());
    }

    @Test
    void valoresVaciosSeInformanDisponibles() {
        ProgramaDisponibilidadResponse r = programaService.consultarDisponibilidad("  ", null);

        assertTrue(r.isCodigoDisponible());
        assertTrue(r.isNombreDisponible());
        verifyNoInteractions(programaRepository);
    }

    /* ============================================================
     * Agregar plan a un programa existente
     * ============================================================ */

    @Test
    void agregaPlanAProgramaSinPlanVigente() {
        configurarUsuarioAutenticado();
        when(programaRepository.findById(10L)).thenReturn(Optional.of(programa(10L, true)));
        when(planEstudioRepository.findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc(10L))
                .thenReturn(Optional.empty());

        programaService.agregarPlanInicial(10L, crearPlan("1.0"));

        verify(planEstudioRepository).save(any(PlanEstudio.class));
    }

    @Test
    void noAgregaPlanSiYaTieneUnoVigente() {
        when(programaRepository.findById(10L)).thenReturn(Optional.of(programa(10L, true)));
        when(planEstudioRepository.findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc(10L))
                .thenReturn(Optional.of(new PlanEstudio()));

        assertThrows(
                IllegalArgumentException.class,
                () -> programaService.agregarPlanInicial(10L, crearPlan("2.0"))
        );

        verify(planEstudioRepository, never()).save(any());
    }

    @Test
    void rechazaVersionRepetidaEnElMismoPrograma() {
        configurarUsuarioAutenticado();
        when(programaRepository.findById(10L)).thenReturn(Optional.of(programa(10L, true)));
        when(planEstudioRepository.findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc(10L))
                .thenReturn(Optional.empty());
        when(planEstudioRepository.existsByIdProgramaAndVersionIgnoreCase(10L, "1.0")).thenReturn(true);

        assertThrows(
                DuplicateResourceException.class,
                () -> programaService.agregarPlanInicial(10L, crearPlan("1.0"))
        );
    }

    /* ============================================================
     * Modificar y cambiar estado
     * ============================================================ */

    @Test
    void actualizaNombreYDescripcionSinTocarElCodigo() {
        configurarUsuarioAutenticado();
        Programa existente = programa(10L, true);
        when(programaRepository.findById(10L)).thenReturn(Optional.of(existente));

        ProgramaUpdateRequest request = new ProgramaUpdateRequest();
        request.setNombre("Nombre nuevo");
        request.setDescripcion("Descripción nueva");

        programaService.actualizar(10L, request);

        assertEquals("FMI", existente.getCodigo());
        assertEquals("Nombre nuevo", existente.getNombre());
        assertEquals("Descripción nueva", existente.getDescripcion());
        assertEquals(7L, existente.getIdUsuarioUltimaModificacion());
    }

    @Test
    void noActualizaConNombreDeOtroPrograma() {
        when(programaRepository.findById(10L)).thenReturn(Optional.of(programa(10L, true)));
        when(programaRepository.existsByNombreIgnoreCaseAndIdNot("Repetido", 10L)).thenReturn(true);

        ProgramaUpdateRequest request = new ProgramaUpdateRequest();
        request.setNombre("Repetido");

        assertThrows(DuplicateResourceException.class, () -> programaService.actualizar(10L, request));
        verify(programaRepository, never()).save(any());
    }

    @Test
    void desactivaUnProgramaActivo() {
        configurarUsuarioAutenticado();
        Programa existente = programa(10L, true);
        when(programaRepository.findById(10L)).thenReturn(Optional.of(existente));

        ProgramaDetalleResponse r = programaService.cambiarEstado(10L, false);

        assertFalse(existente.getActivo());
        assertFalse(r.getActivo());
    }

    @Test
    void noCambiaEstadoSiYaEstaEnEseEstado() {
        when(programaRepository.findById(10L)).thenReturn(Optional.of(programa(10L, false)));

        assertThrows(IllegalArgumentException.class, () -> programaService.cambiarEstado(10L, false));
    }

    @Test
    void programaInexistenteDevuelve404() {
        when(programaRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> programaService.obtener(99L));

        assertEquals(404, ex.getStatusCode().value());
    }

    /* ============================================================
     * Listado de gestión
     * ============================================================ */

    @Test
    void listadoIncluyePlanVigenteYCantidadDeCursos() {
        Programa conPlan = programa(1L, true);
        Programa sinPlan = programa(2L, false);
        when(programaRepository.findAllByOrderByNombreAsc()).thenReturn(List.of(conPlan, sinPlan));

        PlanEstudio anterior = plan(30L, "1.0", false);
        PlanEstudio vigente = plan(31L, "2.0", true);
        when(planEstudioRepository.findByIdProgramaOrderByFechaVigenciaDesdeDesc(1L))
                .thenReturn(List.of(vigente, anterior));
        when(planEstudioRepository.findByIdProgramaOrderByFechaVigenciaDesdeDesc(2L))
                .thenReturn(List.of());
        when(mallaCurricularRepository.countByIdPlanEstudioAndActivoTrue(31L)).thenReturn(5L);

        List<ProgramaDetalleResponse> lista = programaService.listarParaGestion();

        assertEquals(2, lista.size());
        assertEquals("2.0", lista.get(0).getPlanVigente().getVersion());
        assertEquals(2, lista.get(0).getCantidadPlanes());
        assertEquals(5L, lista.get(0).getCantidadCursos());

        assertNull(lista.get(1).getPlanVigente());
        assertEquals(0L, lista.get(1).getCantidadCursos());
    }

    // ------------------------------------------------------------

    private ProgramaCreateRequest crearRequest(String codigo, String nombre) {
        ProgramaCreateRequest r = new ProgramaCreateRequest();
        r.setCodigo(codigo);
        r.setNombre(nombre);
        return r;
    }

    private PlanEstudioInicialRequest crearPlan(String version) {
        PlanEstudioInicialRequest p = new PlanEstudioInicialRequest();
        p.setVersion(version);
        p.setFechaVigenciaDesde(LocalDate.of(2027, 2, 1));
        p.setNotaMinimaAprobacion(new BigDecimal("70.00"));
        return p;
    }

    private Programa programa(Long id, boolean activo) {
        Programa p = new Programa();
        p.setId(id);
        p.setCodigo("FMI");
        p.setNombre("Formación Musical Infantil");
        p.setActivo(activo);
        return p;
    }

    private PlanEstudio plan(Long id, String version, boolean vigente) {
        PlanEstudio p = new PlanEstudio();
        p.setId(id);
        p.setVersion(version);
        p.setVigente(vigente);
        return p;
    }

    private void configurarUsuarioAutenticado() {
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(7L);
        when(usuarioRepository.findByCedula("123456789")).thenReturn(Optional.of(usuario));

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("123456789");

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);
    }
}
