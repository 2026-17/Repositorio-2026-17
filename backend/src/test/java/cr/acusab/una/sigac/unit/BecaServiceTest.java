package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Beca;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.TiposBeca;
import cr.acusab.una.sigac.data.entity.TiposEstudiante;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.BecaRepository;
import cr.acusab.una.sigac.data.repository.EstudianteInstrumentoRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.TiposBecaRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.exception.DuplicateResourceException;
import cr.acusab.una.sigac.logic.service.BecaService;
import cr.acusab.una.sigac.logic.service.BecaVigenteService;
import cr.acusab.una.sigac.logic.service.MensualidadCalculoService;
import cr.acusab.una.sigac.presentation.dto.request.BecaActualizarRequest;
import cr.acusab.una.sigac.presentation.dto.request.BecaAsignarRequest;
import cr.acusab.una.sigac.presentation.dto.response.BecaResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BecaServiceTest {

    private BecaRepository becaRepository;
    private EstudianteRepository estudianteRepository;
    private TiposBecaRepository tiposBecaRepository;
    private PeriodoAcademicoRepository periodoAcademicoRepository;
    private EstudianteInstrumentoRepository estudianteInstrumentoRepository;
    private UsuarioRepository usuarioRepository;

    private BecaService becaService;

    @BeforeEach
    void setUp() {
        becaRepository = mock(BecaRepository.class);
        estudianteRepository = mock(EstudianteRepository.class);
        tiposBecaRepository = mock(TiposBecaRepository.class);
        periodoAcademicoRepository = mock(PeriodoAcademicoRepository.class);
        estudianteInstrumentoRepository = mock(EstudianteInstrumentoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);

        becaService = new BecaService(
                becaRepository,
                estudianteRepository,
                tiposBecaRepository,
                periodoAcademicoRepository,
                new MensualidadCalculoService(estudianteInstrumentoRepository),
                usuarioRepository,
                new BecaVigenteService(becaRepository)
        );
    }

    @AfterEach
    void limpiarContextoSeguridad() {
        SecurityContextHolder.clearContext();
    }

    /*
     * ============================================================
     * HU-015 - Asignar beca a un estudiante
     * ============================================================
     */

    @Test
    void asignarBecaCorrectamente() {

        Estudiante estudiante = estudianteActivo();
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));

        TiposBeca tipoBeca = tipoBecaActivo();
        when(tiposBecaRepository.findByIdAndActivoTrue(3)).thenReturn(Optional.of(tipoBeca));

        PeriodoAcademico periodo = periodo();
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo));

        when(becaRepository.existsByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L))
                .thenReturn(false);

        configurarUsuarioAutenticado();

        when(estudianteInstrumentoRepository.countByIdEstudiante_IdAndFechaFinIsNull(1L))
                .thenReturn(1L);

        when(becaRepository.save(any(Beca.class))).thenAnswer(invocation -> {
            Beca beca = invocation.getArgument(0);
            beca.setId(500L);
            return beca;
        });

        BecaAsignarRequest request = new BecaAsignarRequest();
        request.setIdEstudiante(1L);
        request.setIdTipoBeca(3);
        request.setIdPeriodo(2L);
        request.setPorcentaje(BigDecimal.valueOf(50));

        BecaResponse respuesta = becaService.asignar(request);

        assertNotNull(respuesta);
        assertEquals(500L, respuesta.getIdBeca());
        assertEquals(1L, respuesta.getIdEstudiante());
        assertEquals("Ana Pérez", respuesta.getNombreEstudiante());
        assertEquals(BigDecimal.valueOf(50), respuesta.getPorcentaje());

        // Un solo instrumento -> mensualidad base sin recargo.
        assertEquals(new BigDecimal("27000.00"), respuesta.getMontoMensualidadBase());
        assertEquals(new BigDecimal("13500.00"), respuesta.getMontoDescuento());
        assertEquals(new BigDecimal("13500.00"), respuesta.getMontoAPagar());
        assertTrue(respuesta.getActiva());

        ArgumentCaptor<Beca> captor = ArgumentCaptor.forClass(Beca.class);
        verify(becaRepository).save(captor.capture());
        Beca becaGuardada = captor.getValue();

        assertEquals(estudiante, becaGuardada.getIdEstudiante());
        assertEquals(tipoBeca, becaGuardada.getIdTipoBeca());
        assertEquals(periodo, becaGuardada.getIdPeriodo());
        assertTrue(becaGuardada.getActiva());
        assertEquals(7L, becaGuardada.getIdUsuarioAsigna().getId());
    }

    @Test
    void asignarBecaCalculaRecargoPorInstrumentosAdicionales() {

        Estudiante estudiante = estudianteActivo();
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));
        when(tiposBecaRepository.findByIdAndActivoTrue(3)).thenReturn(Optional.of(tipoBecaActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));
        when(becaRepository.existsByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L))
                .thenReturn(false);
        configurarUsuarioAutenticado();

        // Estudiante lleva 3 instrumentos vigentes -> 2 adicionales.
        when(estudianteInstrumentoRepository.countByIdEstudiante_IdAndFechaFinIsNull(1L))
                .thenReturn(3L);

        when(becaRepository.save(any(Beca.class))).thenAnswer(invocation -> {
            Beca beca = invocation.getArgument(0);
            beca.setId(500L);
            return beca;
        });

        BecaAsignarRequest request = new BecaAsignarRequest();
        request.setIdEstudiante(1L);
        request.setIdTipoBeca(3);
        request.setIdPeriodo(2L);
        request.setPorcentaje(BigDecimal.valueOf(25));

        BecaResponse respuesta = becaService.asignar(request);

        // 27000 + 2 * 15000 = 57000
        assertEquals(new BigDecimal("57000.00"), respuesta.getMontoMensualidadBase());
        assertEquals(new BigDecimal("14250.00"), respuesta.getMontoDescuento());
        assertEquals(new BigDecimal("42750.00"), respuesta.getMontoAPagar());
    }

    @Test
    void noDebeAsignarBecaSiEstudianteNoExiste() {

        when(estudianteRepository.findById(1L)).thenReturn(Optional.empty());

        BecaAsignarRequest request = new BecaAsignarRequest();
        request.setIdEstudiante(1L);
        request.setIdTipoBeca(3);
        request.setIdPeriodo(2L);
        request.setPorcentaje(BigDecimal.valueOf(50));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> becaService.asignar(request)
        );

        assertEquals("El estudiante indicado no existe.", exception.getMessage());
        verify(becaRepository, never()).save(any(Beca.class));
    }

    @Test
    void noDebeAsignarBecaSiEstudianteEstaInactivo() {

        Estudiante estudiante = estudianteActivo();
        estudiante.setActivo(false);
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));

        BecaAsignarRequest request = new BecaAsignarRequest();
        request.setIdEstudiante(1L);
        request.setIdTipoBeca(3);
        request.setIdPeriodo(2L);
        request.setPorcentaje(BigDecimal.valueOf(50));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> becaService.asignar(request)
        );

        assertEquals("El estudiante indicado se encuentra inactivo.", exception.getMessage());
        verify(becaRepository, never()).save(any(Beca.class));
    }

    @Test
    void noDebeAsignarBecaConPorcentajeNoPermitido() {

        Estudiante estudiante = estudianteActivo();
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));
        when(tiposBecaRepository.findByIdAndActivoTrue(3)).thenReturn(Optional.of(tipoBecaActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));

        BecaAsignarRequest request = new BecaAsignarRequest();
        request.setIdEstudiante(1L);
        request.setIdTipoBeca(3);
        request.setIdPeriodo(2L);
        request.setPorcentaje(BigDecimal.valueOf(10));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> becaService.asignar(request)
        );

        assertEquals("El porcentaje de beca debe ser 0%, 25%, 50%, 75% o 100%.", exception.getMessage());
        verify(becaRepository, never()).save(any(Beca.class));
    }

    @Test
    void noDebeAsignarBecaConPorcentajeSuperiorA100() {

        Estudiante estudiante = estudianteActivo();
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));
        when(tiposBecaRepository.findByIdAndActivoTrue(3)).thenReturn(Optional.of(tipoBecaActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));

        BecaAsignarRequest request = new BecaAsignarRequest();
        request.setIdEstudiante(1L);
        request.setIdTipoBeca(3);
        request.setIdPeriodo(2L);
        request.setPorcentaje(BigDecimal.valueOf(150));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> becaService.asignar(request)
        );

        assertEquals(
                "El porcentaje de beca no puede ser menor a 0% ni superior a 100%.",
                exception.getMessage()
        );
        verify(becaRepository, never()).save(any(Beca.class));
    }

    @Test
    void noDebeAsignarBecaSiYaTieneUnaActivaEnElPeriodo() {

        Estudiante estudiante = estudianteActivo();
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));
        when(tiposBecaRepository.findByIdAndActivoTrue(3)).thenReturn(Optional.of(tipoBecaActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));
        when(becaRepository.existsByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L))
                .thenReturn(true);

        BecaAsignarRequest request = new BecaAsignarRequest();
        request.setIdEstudiante(1L);
        request.setIdTipoBeca(3);
        request.setIdPeriodo(2L);
        request.setPorcentaje(BigDecimal.valueOf(50));

        assertThrows(
                DuplicateResourceException.class,
                () -> becaService.asignar(request)
        );

        verify(becaRepository, never()).save(any(Beca.class));
    }

    /*
     * ============================================================
     * HU-015 - Modificar una beca asignada
     * ============================================================
     */

    @Test
    void actualizarBecaCorrectamente() {

        Beca beca = becaExistente();
        when(becaRepository.findById(500L)).thenReturn(Optional.of(beca));

        TiposBeca nuevoTipo = tipoBecaActivo();
        nuevoTipo.setId(4);
        when(tiposBecaRepository.findByIdAndActivoTrue(4)).thenReturn(Optional.of(nuevoTipo));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));
        when(becaRepository.save(any(Beca.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(estudianteInstrumentoRepository.countByIdEstudiante_IdAndFechaFinIsNull(1L))
                .thenReturn(1L);

        BecaActualizarRequest request = new BecaActualizarRequest();
        request.setIdTipoBeca(4);
        request.setIdPeriodo(2L);
        request.setPorcentaje(BigDecimal.valueOf(100));
        request.setFechaFin(LocalDate.of(2026, 12, 15));
        request.setMotivo("Actualización de porcentaje");

        BecaResponse respuesta = becaService.actualizar(500L, request);

        assertEquals(BigDecimal.valueOf(100), respuesta.getPorcentaje());
        assertEquals(new BigDecimal("27000.00"), respuesta.getMontoDescuento());
        assertEquals(new BigDecimal("0.00"), respuesta.getMontoAPagar());
        assertEquals(LocalDate.of(2026, 12, 15), respuesta.getFechaFin());
    }

    @Test
    void noDebeActualizarBecaInexistente() {

        when(becaRepository.findById(999L)).thenReturn(Optional.empty());

        BecaActualizarRequest request = new BecaActualizarRequest();
        request.setIdTipoBeca(4);
        request.setIdPeriodo(2L);
        request.setPorcentaje(BigDecimal.valueOf(100));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> becaService.actualizar(999L, request)
        );

        assertEquals("La beca indicada no existe.", exception.getMessage());
    }

    @Test
    void noDebeActualizarFechaFinAnteriorAFechaInicio() {

        Beca beca = becaExistente();
        when(becaRepository.findById(500L)).thenReturn(Optional.of(beca));
        when(tiposBecaRepository.findByIdAndActivoTrue(4)).thenReturn(Optional.of(tipoBecaActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));

        BecaActualizarRequest request = new BecaActualizarRequest();
        request.setIdTipoBeca(4);
        request.setIdPeriodo(2L);
        request.setPorcentaje(BigDecimal.valueOf(50));
        request.setFechaFin(LocalDate.of(2020, 1, 1));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> becaService.actualizar(500L, request)
        );

        assertEquals(
                "La fecha de fin no puede ser anterior a la fecha de inicio de la beca.",
                exception.getMessage()
        );
        verify(becaRepository, never()).save(any(Beca.class));
    }

    /*
     * ============================================================
     * HU-015 - Eliminar una beca asignada
     * ============================================================
     */

    @Test
    void eliminarBecaCorrectamente() {

        Beca beca = becaExistente();
        when(becaRepository.findById(500L)).thenReturn(Optional.of(beca));

        becaService.eliminar(500L);

        verify(becaRepository).delete(beca);
    }

    @Test
    void noDebeEliminarBecaInexistente() {

        when(becaRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> becaService.eliminar(999L)
        );

        assertEquals("La beca indicada no existe.", exception.getMessage());
        verify(becaRepository, never()).delete(any(Beca.class));
    }

    /*
     * ============================================================
     * HU-015 - Consultar becas de un estudiante
     * ============================================================
     */

    @Test
    void listarBecasDeEstudianteCorrectamente() {

        when(estudianteRepository.existsById(1L)).thenReturn(true);
        when(estudianteInstrumentoRepository.countByIdEstudiante_IdAndFechaFinIsNull(1L))
                .thenReturn(1L);
        when(becaRepository.findByIdEstudiante_IdOrderByFechaInicioDesc(1L))
                .thenReturn(List.of(becaExistente()));

        List<BecaResponse> resultado = becaService.listarPorEstudiante(1L);

        assertEquals(1, resultado.size());
        assertEquals(500L, resultado.get(0).getIdBeca());
    }

    @Test
    void noDebeListarBecasDeEstudianteInexistente() {

        when(estudianteRepository.existsById(1L)).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> becaService.listarPorEstudiante(1L)
        );

        assertEquals("El estudiante indicado no existe.", exception.getMessage());
    }

    /*
     * ============================================================
     * Estudiantes de inclusión y beca permanente
     * ============================================================
     */

    @Test
    void noDebeAsignarBecaAEstudianteDeInclusion() {

        Estudiante estudiante = estudianteActivo();
        estudiante.setIdTipoEstudiante(tipoEstudiante("INCLUSION"));
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));

        BecaAsignarRequest request = new BecaAsignarRequest();
        request.setIdEstudiante(1L);
        request.setIdTipoBeca(3);
        request.setIdPeriodo(2L);
        request.setPorcentaje(BigDecimal.valueOf(50));

        BusinessException exception = assertThrows(BusinessException.class, () -> becaService.asignar(request));

        assertEquals("Los estudiantes de inclusión ya tienen beca del 100%", exception.getMessage());
        verify(becaRepository, never()).save(any());
    }

    @Test
    void noDebeAsignarAManoElTipoDeBecaInclusion() {

        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));
        when(tiposBecaRepository.findByIdAndActivoTrue(9)).thenReturn(Optional.of(tipoBecaInclusion()));

        BecaAsignarRequest request = new BecaAsignarRequest();
        request.setIdEstudiante(1L);
        request.setIdTipoBeca(9);
        request.setIdPeriodo(2L);
        request.setPorcentaje(BigDecimal.valueOf(100));

        BusinessException exception = assertThrows(BusinessException.class, () -> becaService.asignar(request));

        assertTrue(exception.getMessage().contains("se asigna automáticamente"));
        verify(becaRepository, never()).save(any());
    }

    @Test
    void noDebeModificarLaBecaPermanenteDeInclusion() {

        when(becaRepository.findById(800L)).thenReturn(Optional.of(becaPermanente()));

        BecaActualizarRequest request = new BecaActualizarRequest();
        request.setIdTipoBeca(3);
        request.setIdPeriodo(2L);
        request.setPorcentaje(BigDecimal.valueOf(50));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> becaService.actualizar(800L, request)
        );

        assertEquals(
                "La beca permanente de inclusión no se puede modificar: se maneja con el tipo de estudiante.",
                exception.getMessage()
        );
        verify(becaRepository, never()).save(any());
    }

    @Test
    void noDebeEliminarLaBecaPermanenteDeInclusion() {

        when(becaRepository.findById(800L)).thenReturn(Optional.of(becaPermanente()));

        BusinessException exception = assertThrows(BusinessException.class, () -> becaService.eliminar(800L));

        assertEquals(
                "La beca permanente de inclusión no se puede eliminar: se maneja con el tipo de estudiante.",
                exception.getMessage()
        );
        verify(becaRepository, never()).delete(any());
    }

    @Test
    void actualizarBecaSinPeriodoNoFalla() {

        // Beca cuyo período se borró (id_periodo quedó NULL), no de inclusión
        Beca beca = becaExistente();
        beca.setIdPeriodo(null);
        when(becaRepository.findById(500L)).thenReturn(Optional.of(beca));
        when(tiposBecaRepository.findByIdAndActivoTrue(3)).thenReturn(Optional.of(tipoBecaActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));
        when(becaRepository.existsByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L)).thenReturn(false);
        when(becaRepository.save(any(Beca.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BecaActualizarRequest request = new BecaActualizarRequest();
        request.setIdTipoBeca(3);
        request.setIdPeriodo(2L);
        request.setPorcentaje(BigDecimal.valueOf(75));

        BecaResponse respuesta = becaService.actualizar(500L, request);

        assertEquals(2L, respuesta.getIdPeriodo());
        assertEquals(BigDecimal.valueOf(75), respuesta.getPorcentaje());
    }

    @Test
    void asignarBecaPermanenteDeInclusionCreaBecaDel100SinPeriodo() {

        when(tiposBecaRepository.findByCodigo("INCLUSION")).thenReturn(Optional.of(tipoBecaInclusion()));
        when(estudianteInstrumentoRepository.countByIdEstudiante_IdAndFechaFinIsNull(1L)).thenReturn(2L);
        when(becaRepository.save(any(Beca.class))).thenAnswer(invocation -> {
            Beca beca = invocation.getArgument(0);
            beca.setId(800L);
            return beca;
        });

        BecaResponse respuesta = becaService.asignarBecaPermanenteInclusion(estudianteActivo());

        ArgumentCaptor<Beca> captor = ArgumentCaptor.forClass(Beca.class);
        verify(becaRepository).save(captor.capture());
        Beca guardada = captor.getValue();
        assertEquals("INCLUSION", guardada.getIdTipoBeca().getCodigo());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(guardada.getPorcentaje()));
        assertNull(guardada.getIdPeriodo());
        assertNull(guardada.getFechaFin());
        assertEquals(LocalDate.now(), guardada.getFechaInicio());
        assertTrue(guardada.getActiva());
        assertEquals("Estudiante de inclusión", guardada.getMotivo());

        // Cubre la mensualidad base y el instrumento adicional
        assertTrue(respuesta.isPermanente());
        assertNull(respuesta.getIdPeriodo());
        assertEquals(new BigDecimal("42000.00"), respuesta.getMontoMensualidadBase());
        assertEquals(0, BigDecimal.ZERO.compareTo(respuesta.getMontoAPagar()));
    }

    @Test
    void asignarBecaPermanenteNoDuplicaSiYaTieneUnaActiva() {

        when(becaRepository.findByIdEstudiante_IdAndIdTipoBeca_CodigoAndIdPeriodoIsNullAndActivaTrue(1L, "INCLUSION"))
                .thenReturn(List.of(becaPermanente()));
        when(estudianteInstrumentoRepository.countByIdEstudiante_IdAndFechaFinIsNull(1L)).thenReturn(1L);

        BecaResponse respuesta = becaService.asignarBecaPermanenteInclusion(estudianteActivo());

        assertEquals(800L, respuesta.getIdBeca());
        verify(becaRepository, never()).save(any());
    }

    @Test
    void cerrarBecaPermanenteLaDesactivaSinBorrarla() {

        Beca permanente = becaPermanente();
        when(becaRepository.findByIdEstudiante_IdAndIdTipoBeca_CodigoAndIdPeriodoIsNullAndActivaTrue(1L, "INCLUSION"))
                .thenReturn(List.of(permanente));

        becaService.cerrarBecaPermanenteInclusion(1L);

        assertFalse(permanente.getActiva());
        assertEquals(LocalDate.now(), permanente.getFechaFin());
        verify(becaRepository).save(permanente);
        verify(becaRepository, never()).delete(any());
    }

    @Test
    void listarBecasIncluyeLaPermanenteSinPeriodo() {

        when(estudianteRepository.existsById(1L)).thenReturn(true);
        when(estudianteInstrumentoRepository.countByIdEstudiante_IdAndFechaFinIsNull(1L)).thenReturn(1L);
        when(becaRepository.findByIdEstudiante_IdOrderByFechaInicioDesc(1L))
                .thenReturn(List.of(becaPermanente(), becaExistente()));

        List<BecaResponse> resultado = becaService.listarPorEstudiante(1L);

        assertEquals(2, resultado.size());
        assertTrue(resultado.get(0).isPermanente());
        assertNull(resultado.get(0).getIdPeriodo());
        assertNull(resultado.get(0).getCodigoPeriodo());
        assertFalse(resultado.get(1).isPermanente());
    }

    @Test
    void listarTiposBecaNoOfreceElTipoInclusion() {

        when(tiposBecaRepository.findByActivoTrueOrderByNombreAsc())
                .thenReturn(List.of(tipoBecaInclusion(), tipoBecaActivo()));

        var tipos = becaService.listarTiposBeca();

        assertEquals(1, tipos.size());
        assertEquals("SOCIOECONOMICA", tipos.get(0).getCodigo());
    }

    /*
     * ============================================================
     * Métodos auxiliares para las pruebas
     * ============================================================
     */

    private TiposEstudiante tipoEstudiante(String codigo) {
        TiposEstudiante tipo = new TiposEstudiante();
        tipo.setId("INCLUSION".equals(codigo) ? 2 : 1);
        tipo.setCodigo(codigo);
        tipo.setNombre(codigo);
        tipo.setActivo(true);
        return tipo;
    }

    private TiposBeca tipoBecaInclusion() {
        TiposBeca tipo = new TiposBeca();
        tipo.setId(9);
        tipo.setCodigo("INCLUSION");
        tipo.setNombre("Inclusión");
        tipo.setActivo(true);
        return tipo;
    }

    private Beca becaPermanente() {
        Beca beca = new Beca();
        beca.setId(800L);
        beca.setIdEstudiante(estudianteActivo());
        beca.setIdTipoBeca(tipoBecaInclusion());
        beca.setIdPeriodo(null);
        beca.setPorcentaje(BigDecimal.valueOf(100));
        beca.setFechaInicio(LocalDate.of(2026, 1, 15));
        beca.setMotivo("Estudiante de inclusión");
        beca.setActiva(true);
        return beca;
    }

    private Estudiante estudianteActivo() {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(1L);
        estudiante.setNombres("Ana");
        estudiante.setPrimerApellido("Pérez");
        estudiante.setActivo(true);
        return estudiante;
    }

    private TiposBeca tipoBecaActivo() {
        TiposBeca tipoBeca = new TiposBeca();
        tipoBeca.setId(3);
        tipoBeca.setCodigo("SOCIOECONOMICA");
        tipoBeca.setNombre("Beca socioeconomica");
        tipoBeca.setActivo(true);
        return tipoBeca;
    }

    private PeriodoAcademico periodo() {
        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setId(2L);
        periodo.setCodigo("2026-1");
        periodo.setNombre("Primer período 2026");
        return periodo;
    }

    private Beca becaExistente() {
        Beca beca = new Beca();
        beca.setId(500L);
        beca.setIdEstudiante(estudianteActivo());
        beca.setIdTipoBeca(tipoBecaActivo());
        beca.setIdPeriodo(periodo());
        beca.setPorcentaje(BigDecimal.valueOf(50));
        beca.setFechaInicio(LocalDate.of(2026, 1, 10));
        beca.setActiva(true);
        return beca;
    }

    private void configurarUsuarioAutenticado() {

        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(7L);
        when(usuarioRepository.findByCedula("123456789")).thenReturn(Optional.of(usuario));

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("123456789");

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);
    }
}
