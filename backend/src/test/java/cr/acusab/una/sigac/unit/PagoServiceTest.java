package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.Beca;
import cr.acusab.una.sigac.data.entity.DocumentosEstudiante;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.MensualidadesEstudiante;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.TiposDocumento;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.AutorizacionesMatriculaRepository;
import cr.acusab.una.sigac.data.repository.BecaRepository;
import cr.acusab.una.sigac.data.repository.DocumentoEstudianteRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.MensualidadEstudianteRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.TipoDocumentoRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.service.AutorizacionMatriculaService;
import cr.acusab.una.sigac.logic.service.MensualidadCalculoService;
import cr.acusab.una.sigac.logic.service.PagoService;
import cr.acusab.una.sigac.presentation.dto.request.RegistrarPagoRequest;
import cr.acusab.una.sigac.presentation.dto.request.RegistrarPagosRequest;
import cr.acusab.una.sigac.presentation.dto.response.EstadoMesPagoResponse;
import cr.acusab.una.sigac.presentation.dto.response.PagoResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PagoServiceTest {

    private MensualidadEstudianteRepository mensualidadEstudianteRepository;
    private EstudianteRepository estudianteRepository;
    private PeriodoAcademicoRepository periodoAcademicoRepository;
    private BecaRepository becaRepository;
    private MensualidadCalculoService mensualidadCalculoService;
    private DocumentoEstudianteRepository documentoEstudianteRepository;
    private TipoDocumentoRepository tipoDocumentoRepository;
    private UsuarioRepository usuarioRepository;
    private AutorizacionesMatriculaRepository autorizacionesMatriculaRepository;
    private AutorizacionMatriculaService autorizacionMatriculaService;

    private PagoService pagoService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mensualidadEstudianteRepository = mock(MensualidadEstudianteRepository.class);
        estudianteRepository = mock(EstudianteRepository.class);
        periodoAcademicoRepository = mock(PeriodoAcademicoRepository.class);
        becaRepository = mock(BecaRepository.class);
        mensualidadCalculoService = mock(MensualidadCalculoService.class);
        documentoEstudianteRepository = mock(DocumentoEstudianteRepository.class);
        tipoDocumentoRepository = mock(TipoDocumentoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        autorizacionesMatriculaRepository = mock(AutorizacionesMatriculaRepository.class);
        autorizacionMatriculaService = mock(AutorizacionMatriculaService.class);

        pagoService = new PagoService(
                mensualidadEstudianteRepository,
                estudianteRepository,
                periodoAcademicoRepository,
                becaRepository,
                mensualidadCalculoService,
                documentoEstudianteRepository,
                tipoDocumentoRepository,
                usuarioRepository,
                autorizacionesMatriculaRepository,
                autorizacionMatriculaService
        );

        ReflectionTestUtils.setField(pagoService, "storageDirectory", tempDir.toString());
    }

    @AfterEach
    void limpiarContextoSeguridad() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registrarPagoCorrectamente() {

        Estudiante estudiante = estudianteActivo();
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));

        PeriodoAcademico periodo = periodo();
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo));

        when(becaRepository.findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L))
                .thenReturn(Optional.empty());

        when(mensualidadEstudianteRepository.findByIdEstudiante_IdAndAnioAndMes(1L, 2026, (short) 3))
                .thenReturn(Optional.empty());

        configurarUsuarioAutenticado();

        TiposDocumento tipoDocumento = new TiposDocumento();
        tipoDocumento.setId(5);
        tipoDocumento.setCodigo("COMPROBANTE_MENSUALIDAD");
        when(tipoDocumentoRepository.findByCodigoAndActivoTrue("COMPROBANTE_MENSUALIDAD"))
                .thenReturn(Optional.of(tipoDocumento));

        when(documentoEstudianteRepository.save(any(DocumentosEstudiante.class))).thenAnswer(invocation -> {
            DocumentosEstudiante documento = invocation.getArgument(0);
            documento.setId(900L);
            return documento;
        });

        when(mensualidadEstudianteRepository.save(any(MensualidadesEstudiante.class))).thenAnswer(invocation -> {
            MensualidadesEstudiante m = invocation.getArgument(0);
            m.setId(700L);
            return m;
        });

        when(mensualidadCalculoService.calcularMontoBase(1L)).thenReturn(new BigDecimal("27000.00"));
        when(mensualidadCalculoService.calcularMontoNeto(new BigDecimal("27000.00"), null))
                .thenReturn(new BigDecimal("27000.00"));

        RegistrarPagoRequest request = new RegistrarPagoRequest();
        request.setIdEstudiante(1L);
        request.setIdPeriodo(2L);
        request.setAnio(2026);
        request.setMes((short) 3);
        request.setFechaPago(LocalDate.of(2026, 3, 10));

        MultipartFile comprobante = new MockMultipartFile(
                "comprobante", "comprobante.pdf", "application/pdf", "%PDF-1.4 contenido de prueba".getBytes());

        PagoResponse respuesta = pagoService.registrar(request, comprobante);

        assertNotNull(respuesta);
        assertEquals(700L, respuesta.getIdMensualidad());
        assertEquals(1L, respuesta.getIdEstudiante());
        assertEquals("Ana Pérez", respuesta.getNombreEstudiante());
        assertEquals(2026, respuesta.getAnio());
        assertEquals((short) 3, respuesta.getMes());
        assertEquals(new BigDecimal("27000.00"), respuesta.getMonto());
        assertTrue(respuesta.getPagada());
        assertEquals("Pago registrado correctamente.", respuesta.getMensaje());

        ArgumentCaptor<MensualidadesEstudiante> captor = ArgumentCaptor.forClass(MensualidadesEstudiante.class);
        verify(mensualidadEstudianteRepository).save(captor.capture());
        MensualidadesEstudiante guardada = captor.getValue();

        assertEquals(estudiante, guardada.getIdEstudiante());
        assertEquals(periodo, guardada.getIdPeriodo());
        assertEquals(2026, guardada.getAnio());
        assertEquals((short) 3, guardada.getMes());
        assertTrue(guardada.getPagada());
        assertNotNull(guardada.getFechaConfirmacion());
        assertEquals(7L, guardada.getIdUsuarioConfirma().getId());
        assertNotNull(guardada.getIdDocumentoComprobante());

        verify(documentoEstudianteRepository).save(any(DocumentosEstudiante.class));
    }

    @Test
    void noDebeRegistrarSiEstudianteNoExiste() {

        when(estudianteRepository.findById(1L)).thenReturn(Optional.empty());

        RegistrarPagoRequest request = requestValido();
        MultipartFile comprobante = comprobanteValido();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.registrar(request, comprobante)
        );

        assertEquals("El estudiante indicado no existe.", exception.getMessage());
        verify(mensualidadEstudianteRepository, never()).save(any(MensualidadesEstudiante.class));
    }

    @Test
    void noDebeRegistrarSiEstudianteEstaInactivo() {

        Estudiante estudiante = estudianteActivo();
        estudiante.setActivo(false);
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));

        RegistrarPagoRequest request = requestValido();
        MultipartFile comprobante = comprobanteValido();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.registrar(request, comprobante)
        );

        assertEquals("El estudiante indicado se encuentra inactivo.", exception.getMessage());
    }

    @Test
    void noDebeRegistrarSiPeriodoNoExiste() {

        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.empty());

        RegistrarPagoRequest request = requestValido();
        MultipartFile comprobante = comprobanteValido();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.registrar(request, comprobante)
        );

        assertEquals("El período académico indicado no existe.", exception.getMessage());
    }

    @Test
    void noDebeRegistrarSiFechaPagoEsFutura() {

        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));

        RegistrarPagoRequest request = requestValido();
        request.setFechaPago(LocalDate.now().plusDays(1));
        MultipartFile comprobante = comprobanteValido();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.registrar(request, comprobante)
        );

        assertEquals("La fecha de pago no puede ser una fecha futura.", exception.getMessage());
    }

    @Test
    void noDebeRegistrarSiEstudianteTieneBecaDel100PorCientoEnElPeriodo() {

        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));

        Beca beca100 = new Beca();
        beca100.setPorcentaje(BigDecimal.valueOf(100));
        when(becaRepository.findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L))
                .thenReturn(Optional.of(beca100));

        RegistrarPagoRequest request = requestValido();
        MultipartFile comprobante = comprobanteValido();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.registrar(request, comprobante)
        );

        assertEquals(
                "El estudiante tiene una beca del 100% para este período académico; no tiene pagos pendientes.",
                exception.getMessage()
        );
        verify(mensualidadEstudianteRepository, never()).save(any(MensualidadesEstudiante.class));
    }

    @Test
    void noDebeRegistrarSiYaExistePagoParaEseMes() {

        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));
        when(becaRepository.findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L))
                .thenReturn(Optional.empty());

        MensualidadesEstudiante existente = new MensualidadesEstudiante();
        existente.setPagada(true);
        when(mensualidadEstudianteRepository.findByIdEstudiante_IdAndAnioAndMes(1L, 2026, (short) 3))
                .thenReturn(Optional.of(existente));

        RegistrarPagoRequest request = requestValido();
        MultipartFile comprobante = comprobanteValido();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.registrar(request, comprobante)
        );

        assertEquals(
                "Ya existe un pago registrado para este estudiante en ese mes.",
                exception.getMessage()
        );
        verify(mensualidadEstudianteRepository, never()).save(any(MensualidadesEstudiante.class));
    }

    @Test
    void noDebeRegistrarSinComprobante() {

        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));
        when(becaRepository.findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L))
                .thenReturn(Optional.empty());

        RegistrarPagoRequest request = requestValido();
        MultipartFile comprobanteVacio = new MockMultipartFile(
                "comprobante", "vacio.pdf", "application/pdf", new byte[0]);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.registrar(request, comprobanteVacio)
        );

        assertEquals("Debe adjuntar el comprobante de pago.", exception.getMessage());
    }

    @Test
    void noDebeRegistrarConTipoDeArchivoNoPermitido() {

        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));
        when(becaRepository.findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L))
                .thenReturn(Optional.empty());

        RegistrarPagoRequest request = requestValido();
        MultipartFile comprobanteInvalido = new MockMultipartFile(
                "comprobante", "comprobante.txt", "text/plain", "contenido".getBytes());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.registrar(request, comprobanteInvalido)
        );

        assertEquals(
                "El comprobante debe ser un PDF o una imagen (JPG, PNG o WEBP).",
                exception.getMessage()
        );
    }

    @Test
    void listarHistorialCorrectamente() {

        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));

        MensualidadesEstudiante pagoMarzo = new MensualidadesEstudiante();
        pagoMarzo.setId(700L);
        pagoMarzo.setIdEstudiante(estudianteActivo());
        pagoMarzo.setAnio(2026);
        pagoMarzo.setMes((short) 3);
        pagoMarzo.setMonto(new BigDecimal("27000.00"));
        pagoMarzo.setPagada(true);

        MensualidadesEstudiante sinPagar = new MensualidadesEstudiante();
        sinPagar.setPagada(false);

        when(mensualidadEstudianteRepository.findByIdEstudiante_IdOrderByAnioDescMesDesc(1L))
                .thenReturn(List.of(pagoMarzo, sinPagar));

        when(mensualidadCalculoService.calcularMontoBase(1L)).thenReturn(new BigDecimal("27000.00"));
        when(mensualidadCalculoService.calcularMontoNeto(any(), any())).thenReturn(new BigDecimal("27000.00"));

        List<PagoResponse> resultado = pagoService.listarHistorial(1L);

        assertEquals(1, resultado.size());
        assertEquals(700L, resultado.get(0).getIdMensualidad());
    }

    @Test
    void noDebeListarHistorialSiEstudianteNoExiste() {

        when(estudianteRepository.findById(1L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.listarHistorial(1L)
        );

        assertEquals("El estudiante indicado no existe.", exception.getMessage());
    }

    /*
     * ============================================================
     * Consultar estado de pago de un mes especifico
     * ============================================================
     */

    @Test
    void consultarEstadoMesPendienteSinBeca() {

        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));
        when(becaRepository.findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L))
                .thenReturn(Optional.empty());
        when(mensualidadEstudianteRepository.findByIdEstudiante_IdAndAnioAndMes(1L, 2026, (short) 3))
                .thenReturn(Optional.empty());
        when(mensualidadCalculoService.calcularMontoBase(1L)).thenReturn(new BigDecimal("27000.00"));
        when(mensualidadCalculoService.calcularMontoNeto(new BigDecimal("27000.00"), null))
                .thenReturn(new BigDecimal("27000.00"));

        EstadoMesPagoResponse respuesta = pagoService.consultarEstadoMes(1L, 2L, 2026, (short) 3);

        assertFalse(respuesta.getPagada());
        assertFalse(respuesta.isSinPagosPendientes());
        assertEquals(new BigDecimal("27000.00"), respuesta.getMontoEsperado());
        assertNull(respuesta.getIdMensualidad());
    }

    @Test
    void consultarEstadoMesYaPagado() {

        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));
        when(becaRepository.findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L))
                .thenReturn(Optional.empty());

        MensualidadesEstudiante pagada = new MensualidadesEstudiante();
        pagada.setId(700L);
        pagada.setPagada(true);
        pagada.setMonto(new BigDecimal("27000.00"));
        pagada.setFechaPago(LocalDate.of(2026, 3, 5));
        when(mensualidadEstudianteRepository.findByIdEstudiante_IdAndAnioAndMes(1L, 2026, (short) 3))
                .thenReturn(Optional.of(pagada));
        when(mensualidadCalculoService.calcularMontoBase(1L)).thenReturn(new BigDecimal("27000.00"));
        when(mensualidadCalculoService.calcularMontoNeto(new BigDecimal("27000.00"), null))
                .thenReturn(new BigDecimal("27000.00"));

        EstadoMesPagoResponse respuesta = pagoService.consultarEstadoMes(1L, 2L, 2026, (short) 3);

        assertTrue(respuesta.getPagada());
        assertEquals(700L, respuesta.getIdMensualidad());
        assertEquals(new BigDecimal("27000.00"), respuesta.getMonto());
        assertEquals(LocalDate.of(2026, 3, 5), respuesta.getFechaPago());
    }

    @Test
    void consultarEstadoMesConBecaDel100PorCientoNoTienePagosPendientes() {

        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));

        Beca beca100 = new Beca();
        beca100.setPorcentaje(BigDecimal.valueOf(100));
        when(becaRepository.findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L))
                .thenReturn(Optional.of(beca100));

        when(mensualidadEstudianteRepository.findByIdEstudiante_IdAndAnioAndMes(1L, 2026, (short) 3))
                .thenReturn(Optional.empty());
        when(mensualidadCalculoService.calcularMontoBase(1L)).thenReturn(new BigDecimal("27000.00"));
        when(mensualidadCalculoService.calcularMontoNeto(new BigDecimal("27000.00"), BigDecimal.valueOf(100)))
                .thenReturn(BigDecimal.ZERO.setScale(2));

        EstadoMesPagoResponse respuesta = pagoService.consultarEstadoMes(1L, 2L, 2026, (short) 3);

        assertTrue(respuesta.isSinPagosPendientes());
        assertFalse(respuesta.getPagada());
        assertEquals(0, new BigDecimal("0.00").compareTo(respuesta.getMontoEsperado()));
    }

    @Test
    void noDebeConsultarEstadoMesSiEstudianteNoExiste() {

        when(estudianteRepository.findById(1L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.consultarEstadoMes(1L, 2L, 2026, (short) 3)
        );

        assertEquals("El estudiante indicado no existe.", exception.getMessage());
    }

    /*
     * ============================================================
     * Ver y eliminar el comprobante de un pago registrado
     * ============================================================
     */

    @Test
    void obtenerComprobanteCorrectamente() {

        DocumentosEstudiante documento = new DocumentosEstudiante();
        documento.setId(900L);
        documento.setUbicacionArchivo(tempDir.resolve("comprobante.pdf").toString());
        documento.setTipoMime("application/pdf");
        documento.setNombreOriginal("comprobante.pdf");

        MensualidadesEstudiante mensualidad = new MensualidadesEstudiante();
        mensualidad.setId(700L);
        mensualidad.setIdDocumentoComprobante(documento);
        when(mensualidadEstudianteRepository.findById(700L)).thenReturn(Optional.of(mensualidad));

        PagoService.ComprobanteArchivo resultado = pagoService.obtenerComprobante(700L);

        assertEquals(documento.getUbicacionArchivo(), resultado.rutaArchivo());
        assertEquals("application/pdf", resultado.tipoMime());
        assertEquals("comprobante.pdf", resultado.nombreOriginal());
    }

    @Test
    void noDebeObtenerComprobanteSiPagoNoExiste() {

        when(mensualidadEstudianteRepository.findById(700L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.obtenerComprobante(700L)
        );

        assertEquals("El pago indicado no existe.", exception.getMessage());
    }

    @Test
    void noDebeObtenerComprobanteSiPagoNoTieneDocumento() {

        MensualidadesEstudiante mensualidad = new MensualidadesEstudiante();
        mensualidad.setId(700L);
        when(mensualidadEstudianteRepository.findById(700L)).thenReturn(Optional.of(mensualidad));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.obtenerComprobante(700L)
        );

        assertEquals("Este pago no tiene comprobante adjunto.", exception.getMessage());
    }

    @Test
    void eliminarPagoCorrectamente() throws Exception {

        Path archivo = tempDir.resolve("comprobante.pdf");
        java.nio.file.Files.writeString(archivo, "contenido");

        DocumentosEstudiante documento = new DocumentosEstudiante();
        documento.setId(900L);
        documento.setUbicacionArchivo(archivo.toString());

        MensualidadesEstudiante mensualidad = new MensualidadesEstudiante();
        mensualidad.setId(700L);
        mensualidad.setIdDocumentoComprobante(documento);
        when(mensualidadEstudianteRepository.findById(700L)).thenReturn(Optional.of(mensualidad));

        pagoService.eliminarPago(700L);

        verify(mensualidadEstudianteRepository).delete(mensualidad);
        verify(documentoEstudianteRepository).delete(documento);
        assertFalse(java.nio.file.Files.exists(archivo));
    }

    @Test
    void noDebeEliminarPagoInexistente() {

        when(mensualidadEstudianteRepository.findById(700L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.eliminarPago(700L)
        );

        assertEquals("El pago indicado no existe.", exception.getMessage());
        verify(mensualidadEstudianteRepository, never()).delete(any(MensualidadesEstudiante.class));
    }

    /*
     * ============================================================
     * Métodos auxiliares para las pruebas
     * ============================================================
     */

    private Estudiante estudianteActivo() {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(1L);
        estudiante.setNombres("Ana");
        estudiante.setPrimerApellido("Pérez");
        estudiante.setActivo(true);
        return estudiante;
    }

    @Test
    void noDebeRegistrarSiElContenidoNoCoincideConElTipoDeclarado() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));
        when(becaRepository.findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L))
                .thenReturn(Optional.empty());

        // Se declara PDF, pero el contenido es texto (p. ej. un .txt renombrado)
        MultipartFile falso = new MockMultipartFile(
                "comprobante", "comprobante.pdf", "application/pdf", "no soy un pdf".getBytes());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.registrar(requestValido(), falso)
        );

        assertEquals(
                "El contenido del comprobante no corresponde a un PDF o una imagen JPG, PNG o WEBP válida.",
                exception.getMessage()
        );
    }

    @Test
    void noDebeRegistrarUnMesFueraDelPeriodo() {
        PeriodoAcademico periodo = periodo();
        periodo.setFechaInicio(LocalDate.of(2026, 2, 1));
        periodo.setFechaFin(LocalDate.of(2026, 6, 30));
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo));

        RegistrarPagoRequest request = requestValido();
        request.setMes((short) 8);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.registrar(request, comprobanteValido())
        );

        assertEquals("El mes pagado no está dentro del período académico seleccionado.", exception.getMessage());
    }

    /*
     * ============================================================
     * Varios pagos con un solo comprobante (meses + matrícula)
     * ============================================================
     */

    @Test
    void registrarVariosMesesYMatriculaConUnSoloComprobante() {
        Estudiante estudiante = estudianteActivo();
        PeriodoAcademico periodo = periodo();
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudiante));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo));
        when(becaRepository.findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L))
                .thenReturn(Optional.empty());
        when(mensualidadEstudianteRepository.findByIdEstudiante_IdAndAnioAndMes(eq(1L), eq(2026), anyShort()))
                .thenReturn(Optional.empty());
        configurarUsuarioAutenticado();
        configurarTipoDocumento("COMPROBANTE_MENSUALIDAD", 5);
        configurarTipoDocumento("COMPROBANTE_MATRICULA", 6);
        when(documentoEstudianteRepository.save(any(DocumentosEstudiante.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mensualidadEstudianteRepository.save(any(MensualidadesEstudiante.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mensualidadCalculoService.calcularMontoBase(1L)).thenReturn(new BigDecimal("27000.00"));
        when(mensualidadCalculoService.calcularMontoNeto(new BigDecimal("27000.00"), null))
                .thenReturn(new BigDecimal("27000.00"));

        RegistrarPagosRequest request = requestVarios(true, 3, 4);
        request.setObservaciones("  Pagó marzo y abril   juntos ");

        pagoService.registrarVarios(request, comprobanteValido());

        ArgumentCaptor<MensualidadesEstudiante> captor = ArgumentCaptor.forClass(MensualidadesEstudiante.class);
        verify(mensualidadEstudianteRepository, times(2)).save(captor.capture());
        List<MensualidadesEstudiante> guardadas = captor.getAllValues();
        assertEquals((short) 3, guardadas.get(0).getMes());
        assertEquals((short) 4, guardadas.get(1).getMes());
        guardadas.forEach(m -> {
            assertTrue(m.getPagada());
            assertEquals("Pagó marzo y abril juntos", m.getObservaciones());
            assertEquals(new BigDecimal("27000.00"), m.getMonto());
        });
        // Cada mes tiene su propia copia del comprobante
        assertNotSame(guardadas.get(0).getIdDocumentoComprobante(), guardadas.get(1).getIdDocumentoComprobante());

        ArgumentCaptor<DocumentosEstudiante> documentoMatricula = ArgumentCaptor.forClass(DocumentosEstudiante.class);
        verify(autorizacionMatriculaService).registrarPagoMatricula(
                eq(estudiante), eq(periodo), documentoMatricula.capture(), eq("Pagó marzo y abril juntos"));
        assertEquals("COMPROBANTE_MATRICULA", documentoMatricula.getValue().getIdTipoDocumento().getCodigo());
        assertEquals(LocalDate.of(2026, 3, 10), documentoMatricula.getValue().getFechaEmision());
    }

    @Test
    void registrarSoloMatriculaNoRegistraMensualidades() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));
        when(becaRepository.findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L))
                .thenReturn(Optional.empty());
        configurarUsuarioAutenticado();
        configurarTipoDocumento("COMPROBANTE_MATRICULA", 6);
        when(documentoEstudianteRepository.save(any(DocumentosEstudiante.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        pagoService.registrarVarios(requestVarios(true), comprobanteValido());

        verify(mensualidadEstudianteRepository, never()).save(any(MensualidadesEstudiante.class));
        verify(autorizacionMatriculaService).registrarPagoMatricula(any(), any(), any(), isNull());
    }

    @Test
    void noDebeRegistrarVariosSinSeleccionarNada() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.registrarVarios(requestVarios(false), comprobanteValido())
        );

        assertEquals("Seleccione al menos un mes o el pago de matrícula.", exception.getMessage());
    }

    @Test
    void noDebeRegistrarNingunPagoSiUnMesYaEstaPagado() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));
        when(becaRepository.findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L))
                .thenReturn(Optional.empty());
        when(mensualidadEstudianteRepository.findByIdEstudiante_IdAndAnioAndMes(1L, 2026, (short) 3))
                .thenReturn(Optional.empty());
        MensualidadesEstudiante pagada = new MensualidadesEstudiante();
        pagada.setPagada(true);
        when(mensualidadEstudianteRepository.findByIdEstudiante_IdAndAnioAndMes(1L, 2026, (short) 4))
                .thenReturn(Optional.of(pagada));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.registrarVarios(requestVarios(true, 3, 4), comprobanteValido())
        );

        assertEquals("Ya existe un pago registrado para abril 2026.", exception.getMessage());
        verify(mensualidadEstudianteRepository, never()).save(any(MensualidadesEstudiante.class));
        verify(documentoEstudianteRepository, never()).save(any(DocumentosEstudiante.class));
        verify(autorizacionMatriculaService, never()).registrarPagoMatricula(any(), any(), any(), any());
    }

    @Test
    void noDebeRegistrarNingunPagoSiLaSolicitudDeMatriculaYaFueProcesada() {
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(estudianteActivo()));
        when(periodoAcademicoRepository.findById(2L)).thenReturn(Optional.of(periodo()));
        when(becaRepository.findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(1L, 2L))
                .thenReturn(Optional.empty());
        when(mensualidadEstudianteRepository.findByIdEstudiante_IdAndAnioAndMes(eq(1L), eq(2026), anyShort()))
                .thenReturn(Optional.empty());
        doThrow(new BusinessException("La solicitud de matrícula ya fue procesada."))
                .when(autorizacionMatriculaService).validarPuedeRegistrarPagoMatricula(1L, 2L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> pagoService.registrarVarios(requestVarios(true, 3), comprobanteValido())
        );

        assertEquals("La solicitud de matrícula ya fue procesada.", exception.getMessage());
        verify(mensualidadEstudianteRepository, never()).save(any(MensualidadesEstudiante.class));
        verify(documentoEstudianteRepository, never()).save(any(DocumentosEstudiante.class));
    }

    private RegistrarPagosRequest requestVarios(boolean incluirMatricula, int... meses) {
        RegistrarPagosRequest request = new RegistrarPagosRequest();
        request.setIdEstudiante(1L);
        request.setIdPeriodo(2L);
        request.setIncluirMatricula(incluirMatricula);
        request.setFechaPago(LocalDate.of(2026, 3, 10));
        for (int mes : meses) {
            RegistrarPagosRequest.MesPago mesPago = new RegistrarPagosRequest.MesPago();
            mesPago.setAnio(2026);
            mesPago.setMes((short) mes);
            request.getMeses().add(mesPago);
        }
        return request;
    }

    private void configurarTipoDocumento(String codigo, int id) {
        TiposDocumento tipo = new TiposDocumento();
        tipo.setId(id);
        tipo.setCodigo(codigo);
        when(tipoDocumentoRepository.findByCodigoAndActivoTrue(codigo)).thenReturn(Optional.of(tipo));
    }

    private PeriodoAcademico periodo() {
        PeriodoAcademico periodo = new PeriodoAcademico();
        periodo.setId(2L);
        periodo.setCodigo("2026-1");
        periodo.setNombre("Primer período 2026");
        return periodo;
    }

    private RegistrarPagoRequest requestValido() {
        RegistrarPagoRequest request = new RegistrarPagoRequest();
        request.setIdEstudiante(1L);
        request.setIdPeriodo(2L);
        request.setAnio(2026);
        request.setMes((short) 3);
        request.setFechaPago(LocalDate.of(2026, 3, 10));
        return request;
    }

    private MultipartFile comprobanteValido() {
        return new MockMultipartFile(
                "comprobante", "comprobante.pdf", "application/pdf", "%PDF-1.4 contenido de prueba".getBytes());
    }

    private void configurarUsuarioAutenticado() {

        Usuario usuario = new Usuario();
        usuario.setId(7L);
        when(usuarioRepository.findByCedula("123456789")).thenReturn(Optional.of(usuario));

        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("123456789");

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);
    }
}
