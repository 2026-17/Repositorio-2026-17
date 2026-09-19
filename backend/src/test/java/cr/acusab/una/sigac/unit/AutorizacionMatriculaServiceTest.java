package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.AutorizacionesMatricula;
import cr.acusab.una.sigac.data.entity.DocumentosEstudiante;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.EstadosAutorizacionesMatricula;
import cr.acusab.una.sigac.data.entity.MensualidadesEstudiante;
import cr.acusab.una.sigac.data.entity.NotificacionEstudiante;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.Usuario;

import cr.acusab.una.sigac.data.repository.AutorizacionesMatriculaRepository;
import cr.acusab.una.sigac.data.repository.DocumentoEstudianteRepository;
import cr.acusab.una.sigac.data.repository.EstadosAutorizacionesMatriculaRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.MensualidadEstudianteRepository;
import cr.acusab.una.sigac.data.repository.NotificacionEstudianteRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;

import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.service.AutorizacionMatriculaService;

import cr.acusab.una.sigac.presentation.dto.request.ConfirmarMatriculaRequestDTO;
import cr.acusab.una.sigac.presentation.dto.request.IniciarPreaprobacionRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.AutorizacionMatriculaResponseDTO;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutorizacionMatriculaServiceTest {

    private AutorizacionesMatriculaRepository autorizacionRepository;

    private EstadosAutorizacionesMatriculaRepository estadoRepository;

    private UsuarioRepository usuarioRepository;

    private NotificacionEstudianteRepository notificacionRepository;

    private EstudianteRepository estudianteRepository;

    private PeriodoAcademicoRepository periodoAcademicoRepository;

    private MensualidadEstudianteRepository mensualidadEstudianteRepository;

    private DocumentoEstudianteRepository documentoEstudianteRepository;

    private AutorizacionMatriculaService autorizacionMatriculaService;

    @BeforeEach
    void setUp() {

        autorizacionRepository =
                mock(AutorizacionesMatriculaRepository.class);

        estadoRepository =
                mock(EstadosAutorizacionesMatriculaRepository.class);

        usuarioRepository =
                mock(UsuarioRepository.class);

        notificacionRepository =
                mock(NotificacionEstudianteRepository.class);

        estudianteRepository =
                mock(EstudianteRepository.class);

        periodoAcademicoRepository =
                mock(PeriodoAcademicoRepository.class);

        mensualidadEstudianteRepository =
                mock(MensualidadEstudianteRepository.class);

        documentoEstudianteRepository =
                mock(DocumentoEstudianteRepository.class);

        autorizacionMatriculaService =
                new AutorizacionMatriculaService(
                        autorizacionRepository,
                        estadoRepository,
                        usuarioRepository,
                        notificacionRepository,
                        estudianteRepository,
                        periodoAcademicoRepository,
                        mensualidadEstudianteRepository,
                        documentoEstudianteRepository
                );
    }

    @AfterEach
    void limpiarContextoSeguridad() {
        SecurityContextHolder.clearContext();
    }

    /*
     * ============================================================
     * HU-012 - Listar solicitudes pendientes
     * ============================================================
     */

    @Test
    void debeListarSolicitudesPendientes() {

        AutorizacionesMatricula autorizacion =
                crearAutorizacionPendienteValida();

        when(
                autorizacionRepository
                        .findByIdEstadoAutorizacion_CodigoIgnoreCaseOrderByCreadoEnAsc(
                                "PENDIENTE"
                        )
        ).thenReturn(
                List.of(autorizacion)
        );

        List<AutorizacionMatriculaResponseDTO> resultado =
                autorizacionMatriculaService.listarPendientes();

        assertEquals(
                1,
                resultado.size()
        );

        AutorizacionMatriculaResponseDTO dto =
                resultado.get(0);

        assertEquals(
                10L,
                dto.getIdAutorizacion()
        );

        assertEquals(
                20L,
                dto.getIdEstudiante()
        );

        assertEquals(
                "Ana Sánchez Mora",
                dto.getEstudiante()
        );

        assertEquals(
                30L,
                dto.getIdPeriodo()
        );

        assertEquals(
                "2026-02",
                dto.getCodigoPeriodo()
        );

        assertEquals(
                "Segundo período 2026",
                dto.getPeriodo()
        );

        assertEquals(
                "PENDIENTE",
                dto.getEstado()
        );

        assertTrue(
                dto.getMensualidadesAlDia()
        );

        assertTrue(
                dto.getPagoMatriculaConfirmado()
        );

        assertTrue(
                dto.getDocumentacionCompleta()
        );

        assertEquals(
                40L,
                dto.getIdDocumentoComprobanteMatricula()
        );

        assertEquals(
                "comprobante-matricula.pdf",
                dto.getComprobanteMatricula()
        );

        verify(
                autorizacionRepository
        ).findByIdEstadoAutorizacion_CodigoIgnoreCaseOrderByCreadoEnAsc(
                "PENDIENTE"
        );

        verify(
                notificacionRepository,
                never()
        ).save(
                any(NotificacionEstudiante.class)
        );
    }

    /*
     * ============================================================
     * HU-012 - Listar historial
     * ============================================================
     */

    @Test
    void debeListarHistorialDeSolicitudesProcesadas() {

        AutorizacionesMatricula rechazada =
                crearAutorizacionPendienteValida();

        rechazada.setId(
                11L
        );

        rechazada.setIdEstadoAutorizacion(
                crearEstado(
                        "RECHAZADA"
                )
        );

        rechazada.setFechaRevision(
                Instant.parse(
                        "2026-09-16T08:00:00Z"
                )
        );

        rechazada.setObservaciones(
                "Faltan requisitos administrativos."
        );

        AutorizacionesMatricula autorizada =
                crearAutorizacionPendienteValida();

        autorizada.setId(
                12L
        );

        autorizada.setIdEstadoAutorizacion(
                crearEstado(
                        "AUTORIZADA"
                )
        );

        autorizada.setFechaRevision(
                Instant.parse(
                        "2026-09-15T23:00:00Z"
                )
        );

        when(
                autorizacionRepository
                        .findByIdEstadoAutorizacion_CodigoInOrderByFechaRevisionDesc(
                                List.of(
                                        "AUTORIZADA",
                                        "RECHAZADA"
                                )
                        )
        ).thenReturn(
                List.of(
                        rechazada,
                        autorizada
                )
        );

        List<AutorizacionMatriculaResponseDTO> resultado =
                autorizacionMatriculaService.listarHistorial();

        assertEquals(
                2,
                resultado.size()
        );

        assertEquals(
                11L,
                resultado.get(0).getIdAutorizacion()
        );

        assertEquals(
                "RECHAZADA",
                resultado.get(0).getEstado()
        );

        assertEquals(
                "Faltan requisitos administrativos.",
                resultado.get(0).getObservaciones()
        );

        assertNotNull(
                resultado.get(0).getFechaRevision()
        );

        assertEquals(
                12L,
                resultado.get(1).getIdAutorizacion()
        );

        assertEquals(
                "AUTORIZADA",
                resultado.get(1).getEstado()
        );

        assertNotNull(
                resultado.get(1).getFechaRevision()
        );

        verify(
                autorizacionRepository
        ).findByIdEstadoAutorizacion_CodigoInOrderByFechaRevisionDesc(
                List.of(
                        "AUTORIZADA",
                        "RECHAZADA"
                )
        );

        verify(
                notificacionRepository,
                never()
        ).save(
                any(NotificacionEstudiante.class)
        );
    }

    /*
     * ============================================================
     * HU-012 - Aprobar solicitud
     * ============================================================
     */

    @Test
    void debeAprobarSolicitudConRequisitosValidosYNotificarEstudiante() {

        AutorizacionesMatricula autorizacion =
                crearAutorizacionPendienteValida();

        when(
                autorizacionRepository.findById(
                        10L
                )
        ).thenReturn(
                Optional.of(
                        autorizacion
                )
        );

        Usuario administrativo =
                crearUsuarioAdministrativo();

        when(
                usuarioRepository.findByCedula(
                        "888888888"
                )
        ).thenReturn(
                Optional.of(
                        administrativo
                )
        );

        configurarUsuarioAutenticado();

        EstadosAutorizacionesMatricula autorizada =
                crearEstado(
                        "AUTORIZADA"
                );

        when(
                estadoRepository.findByCodigoAndActivoTrue(
                        "AUTORIZADA"
                )
        ).thenReturn(
                Optional.of(
                        autorizada
                )
        );

        when(
                autorizacionRepository.saveAndFlush(
                        any(AutorizacionesMatricula.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        ConfirmarMatriculaRequestDTO request =
                new ConfirmarMatriculaRequestDTO();

        request.setDecision(
                "APROBAR"
        );

        request.setObservaciones(
                "  Revisión administrativa correcta.  "
        );

        AutorizacionMatriculaResponseDTO resultado =
                autorizacionMatriculaService.confirmar(
                        10L,
                        request
                );

        assertNotNull(
                resultado
        );

        assertEquals(
                "AUTORIZADA",
                resultado.getEstado()
        );

        assertEquals(
                administrativo,
                autorizacion.getIdUsuarioRevisa()
        );

        assertNotNull(
                autorizacion.getFechaRevision()
        );

        assertEquals(
                "Revisión administrativa correcta.",
                autorizacion.getObservaciones()
        );

        assertEquals(
                "administrativo@sigac.test",
                resultado.getRevisadoPor()
        );

        assertNotNull(
                resultado.getFechaRevision()
        );

        verify(
                estadoRepository
        ).findByCodigoAndActivoTrue(
                "AUTORIZADA"
        );

        verify(
                autorizacionRepository
        ).saveAndFlush(
                autorizacion
        );

        /*
         * Verificar la notificación creada al estudiante.
         */
        ArgumentCaptor<NotificacionEstudiante> captor =
                ArgumentCaptor.forClass(
                        NotificacionEstudiante.class
                );

        verify(
                notificacionRepository
        ).save(
                captor.capture()
        );

        NotificacionEstudiante notificacion =
                captor.getValue();

        assertEquals(
                autorizacion.getIdEstudiante(),
                notificacion.getIdEstudiante()
        );

        assertEquals(
                autorizacion,
                notificacion.getIdAutorizacion()
        );

        assertEquals(
                "MATRICULA_APROBADA",
                notificacion.getTipo()
        );

        assertEquals(
                "Solicitud de matrícula aprobada",
                notificacion.getAsunto()
        );

        assertEquals(
                "Su solicitud de matrícula para "
                        + "Segundo período 2026 fue aprobada. "
                        + "Observaciones administrativas: "
                        + "Revisión administrativa correcta.",
                notificacion.getMensaje()
        );

        assertFalse(
                notificacion.getLeida()
        );
    }

    /*
     * ============================================================
     * HU-012 - Rechazar solicitud
     * ============================================================
     */

    @Test
    void debeRechazarSolicitudYNotificarEstudiante() {

        AutorizacionesMatricula autorizacion =
                crearAutorizacionPendienteIncompleta();

        when(
                autorizacionRepository.findById(
                        10L
                )
        ).thenReturn(
                Optional.of(
                        autorizacion
                )
        );

        Usuario administrativo =
                crearUsuarioAdministrativo();

        when(
                usuarioRepository.findByCedula(
                        "888888888"
                )
        ).thenReturn(
                Optional.of(
                        administrativo
                )
        );

        configurarUsuarioAutenticado();

        EstadosAutorizacionesMatricula rechazada =
                crearEstado(
                        "RECHAZADA"
                );

        when(
                estadoRepository.findByCodigoAndActivoTrue(
                        "RECHAZADA"
                )
        ).thenReturn(
                Optional.of(
                        rechazada
                )
        );

        when(
                autorizacionRepository.saveAndFlush(
                        any(AutorizacionesMatricula.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        ConfirmarMatriculaRequestDTO request =
                new ConfirmarMatriculaRequestDTO();

        request.setDecision(
                "RECHAZAR"
        );

        request.setObservaciones(
                "Faltan requisitos administrativos."
        );

        AutorizacionMatriculaResponseDTO resultado =
                autorizacionMatriculaService.confirmar(
                        10L,
                        request
                );

        assertNotNull(
                resultado
        );

        assertEquals(
                "RECHAZADA",
                resultado.getEstado()
        );

        assertNotNull(
                autorizacion.getFechaRevision()
        );

        assertEquals(
                administrativo,
                autorizacion.getIdUsuarioRevisa()
        );

        assertEquals(
                "Faltan requisitos administrativos.",
                resultado.getObservaciones()
        );

        verify(
                estadoRepository
        ).findByCodigoAndActivoTrue(
                "RECHAZADA"
        );

        verify(
                autorizacionRepository
        ).saveAndFlush(
                autorizacion
        );

        /*
         * Verificar la notificación creada al estudiante.
         */
        ArgumentCaptor<NotificacionEstudiante> captor =
                ArgumentCaptor.forClass(
                        NotificacionEstudiante.class
                );

        verify(
                notificacionRepository
        ).save(
                captor.capture()
        );

        NotificacionEstudiante notificacion =
                captor.getValue();

        assertEquals(
                autorizacion.getIdEstudiante(),
                notificacion.getIdEstudiante()
        );

        assertEquals(
                autorizacion,
                notificacion.getIdAutorizacion()
        );

        assertEquals(
                "MATRICULA_RECHAZADA",
                notificacion.getTipo()
        );

        assertEquals(
                "Solicitud de matrícula rechazada",
                notificacion.getAsunto()
        );

        assertEquals(
                "Su solicitud de matrícula para "
                        + "Segundo período 2026 fue rechazada. "
                        + "Observaciones administrativas: "
                        + "Faltan requisitos administrativos.",
                notificacion.getMensaje()
        );

        assertFalse(
                notificacion.getLeida()
        );
    }

    /*
     * ============================================================
     * HU-012 - Validación de requisitos
     * ============================================================
     */

    @Test
    void noDebeAprobarSolicitudConRequisitosIncompletos() {

        AutorizacionesMatricula autorizacion =
                crearAutorizacionPendienteIncompleta();

        when(
                autorizacionRepository.findById(
                        10L
                )
        ).thenReturn(
                Optional.of(
                        autorizacion
                )
        );

        Usuario administrativo =
                crearUsuarioAdministrativo();

        when(
                usuarioRepository.findByCedula(
                        "888888888"
                )
        ).thenReturn(
                Optional.of(
                        administrativo
                )
        );

        configurarUsuarioAutenticado();

        ConfirmarMatriculaRequestDTO request =
                new ConfirmarMatriculaRequestDTO();

        request.setDecision(
                "APROBAR"
        );

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                autorizacionMatriculaService.confirmar(
                                        10L,
                                        request
                                )
                );

        /*
         * El servicio utiliza validación fail-fast.
         *
         * Como el primer requisito inválido son las
         * mensualidades, la ejecución se detiene allí.
         */
        assertEquals(
                "No se puede aprobar la matrícula porque "
                        + "las mensualidades no están al día.",
                exception.getMessage()
        );

        verify(
                estadoRepository,
                never()
        ).findByCodigoAndActivoTrue(
                anyString()
        );

        verify(
                autorizacionRepository,
                never()
        ).saveAndFlush(
                any(AutorizacionesMatricula.class)
        );

        verify(
                notificacionRepository,
                never()
        ).save(
                any(NotificacionEstudiante.class)
        );
    }

    /*
     * ============================================================
     * HU-012 - Evitar doble procesamiento
     * ============================================================
     */

    @Test
    void noDebeProcesarSolicitudYaRevisada() {

        AutorizacionesMatricula autorizacion =
                crearAutorizacionPendienteValida();

        autorizacion.setIdEstadoAutorizacion(
                crearEstado(
                        "AUTORIZADA"
                )
        );

        when(
                autorizacionRepository.findById(
                        10L
                )
        ).thenReturn(
                Optional.of(
                        autorizacion
                )
        );

        ConfirmarMatriculaRequestDTO request =
                new ConfirmarMatriculaRequestDTO();

        request.setDecision(
                "RECHAZAR"
        );

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                autorizacionMatriculaService.confirmar(
                                        10L,
                                        request
                                )
                );

        assertEquals(
                "La solicitud de matrícula ya fue procesada.",
                exception.getMessage()
        );

        verify(
                usuarioRepository,
                never()
        ).findByCedula(
                anyString()
        );

        verify(
                autorizacionRepository,
                never()
        ).saveAndFlush(
                any(AutorizacionesMatricula.class)
        );

        verify(
                notificacionRepository,
                never()
        ).save(
                any(NotificacionEstudiante.class)
        );
    }

    /*
     * ============================================================
     * HU-012 - Solicitud inexistente
     * ============================================================
     */

    @Test
    void noDebeProcesarSolicitudInexistente() {

        when(
                autorizacionRepository.findById(
                        999L
                )
        ).thenReturn(
                Optional.empty()
        );

        ConfirmarMatriculaRequestDTO request =
                new ConfirmarMatriculaRequestDTO();

        request.setDecision(
                "APROBAR"
        );

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                autorizacionMatriculaService.confirmar(
                                        999L,
                                        request
                                )
                );

        assertEquals(
                "La solicitud de matrícula indicada no existe.",
                exception.getMessage()
        );

        verify(
                usuarioRepository,
                never()
        ).findByCedula(
                anyString()
        );

        verify(
                autorizacionRepository,
                never()
        ).saveAndFlush(
                any(AutorizacionesMatricula.class)
        );

        verify(
                notificacionRepository,
                never()
        ).save(
                any(NotificacionEstudiante.class)
        );
    }

    /*
     * ============================================================
     * Historia 1 - Nuevas comprobaciones (estudiante activo y
     * datos completos)
     * ============================================================
     */

    @Test
    void noDebeAprobarSolicitudSiEstudianteNoEstaActivo() {

        AutorizacionesMatricula autorizacion =
                crearAutorizacionPendienteValida();

        autorizacion.setEstudianteActivo(
                false
        );

        when(
                autorizacionRepository.findById(
                        10L
                )
        ).thenReturn(
                Optional.of(
                        autorizacion
                )
        );

        Usuario administrativo =
                crearUsuarioAdministrativo();

        when(
                usuarioRepository.findByCedula(
                        "888888888"
                )
        ).thenReturn(
                Optional.of(
                        administrativo
                )
        );

        configurarUsuarioAutenticado();

        ConfirmarMatriculaRequestDTO request =
                new ConfirmarMatriculaRequestDTO();

        request.setDecision(
                "APROBAR"
        );

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                autorizacionMatriculaService.confirmar(
                                        10L,
                                        request
                                )
                );

        assertEquals(
                "No se puede aprobar la matrícula porque el estudiante no se encuentra activo.",
                exception.getMessage()
        );

        verify(
                autorizacionRepository,
                never()
        ).saveAndFlush(
                any(AutorizacionesMatricula.class)
        );
    }

    @Test
    void noDebeAprobarSolicitudSiDatosNoEstanCompletos() {

        AutorizacionesMatricula autorizacion =
                crearAutorizacionPendienteValida();

        autorizacion.setDatosCompletos(
                false
        );

        when(
                autorizacionRepository.findById(
                        10L
                )
        ).thenReturn(
                Optional.of(
                        autorizacion
                )
        );

        Usuario administrativo =
                crearUsuarioAdministrativo();

        when(
                usuarioRepository.findByCedula(
                        "888888888"
                )
        ).thenReturn(
                Optional.of(
                        administrativo
                )
        );

        configurarUsuarioAutenticado();

        ConfirmarMatriculaRequestDTO request =
                new ConfirmarMatriculaRequestDTO();

        request.setDecision(
                "APROBAR"
        );

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () ->
                                autorizacionMatriculaService.confirmar(
                                        10L,
                                        request
                                )
                );

        assertEquals(
                "No se puede aprobar la matrícula porque los datos personales del estudiante no están completos.",
                exception.getMessage()
        );

        verify(
                autorizacionRepository,
                never()
        ).saveAndFlush(
                any(AutorizacionesMatricula.class)
        );
    }

    /*
     * ============================================================
     * Historia 1 - Iniciar preaprobación
     * ============================================================
     */

    @Test
    void debeIniciarPreaprobacionCalculandoComprobacionesAutomaticas() {

        Estudiante estudiante =
                crearEstudianteConDatosCompletos();

        PeriodoAcademico periodo =
                new PeriodoAcademico();

        periodo.setId(
                30L
        );

        periodo.setCodigo(
                "2026-02"
        );

        periodo.setNombre(
                "Segundo período 2026"
        );

        when(
                estudianteRepository.findById(
                        20L
                )
        ).thenReturn(
                Optional.of(
                        estudiante
                )
        );

        when(
                periodoAcademicoRepository.findById(
                        30L
                )
        ).thenReturn(
                Optional.of(
                        periodo
                )
        );

        when(
                autorizacionRepository
                        .findByIdEstudiante_IdAndIdPeriodo_Id(
                                20L,
                                30L
                        )
        ).thenReturn(
                Optional.empty()
        );

        when(
                estadoRepository.findByCodigoAndActivoTrue(
                        "PENDIENTE"
                )
        ).thenReturn(
                Optional.of(
                        crearEstado(
                                "PENDIENTE"
                        )
                )
        );

        MensualidadesEstudiante mensualidadPagada =
                new MensualidadesEstudiante();

        mensualidadPagada.setAnio(
                LocalDate.now().getYear()
        );

        mensualidadPagada.setMes(
                (short) LocalDate.now().getMonthValue()
        );

        mensualidadPagada.setPagada(
                true
        );

        when(
                mensualidadEstudianteRepository
                        .findByIdEstudiante_IdOrderByAnioDescMesDesc(
                                20L
                        )
        ).thenReturn(
                List.of(
                        mensualidadPagada
                )
        );

        DocumentosEstudiante comprobante =
                new DocumentosEstudiante();

        comprobante.setId(
                40L
        );

        comprobante.setNombreOriginal(
                "comprobante-matricula.pdf"
        );

        when(
                documentoEstudianteRepository
                        .findFirstByIdEstudiante_IdAndIdTipoDocumento_CodigoAndEsActualTrueOrderByCargadoEnDesc(
                                20L,
                                "COMPROBANTE_MATRICULA"
                        )
        ).thenReturn(
                Optional.of(
                        comprobante
                )
        );

        when(
                autorizacionRepository.saveAndFlush(
                        any(AutorizacionesMatricula.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        IniciarPreaprobacionRequestDTO request =
                new IniciarPreaprobacionRequestDTO();

        request.setIdEstudiante(
                20L
        );

        request.setIdPeriodo(
                30L
        );

        AutorizacionMatriculaResponseDTO resultado =
                autorizacionMatriculaService.iniciarPreaprobacion(
                        request
                );

        assertTrue(
                resultado.getEstudianteActivo()
        );

        assertTrue(
                resultado.getDatosCompletos()
        );

        assertTrue(
                resultado.getMensualidadesAlDia()
        );

        assertTrue(
                resultado.getPagoMatriculaConfirmado()
        );

        assertEquals(
                40L,
                resultado.getIdDocumentoComprobanteMatricula()
        );

        assertEquals(
                "PENDIENTE",
                resultado.getEstado()
        );

        assertFalse(
                resultado.getPuedeMatricular()
        );
    }

    /*
     * ============================================================
     * Métodos auxiliares
     * ============================================================
     */

    private AutorizacionesMatricula
    crearAutorizacionPendienteValida() {

        AutorizacionesMatricula autorizacion =
                new AutorizacionesMatricula();

        autorizacion.setId(
                10L
        );

        Estudiante estudiante =
                new Estudiante();

        estudiante.setId(
                20L
        );

        estudiante.setNombres(
                "Ana"
        );

        estudiante.setPrimerApellido(
                "Sánchez"
        );

        estudiante.setSegundoApellido(
                "Mora"
        );

        autorizacion.setIdEstudiante(
                estudiante
        );

        PeriodoAcademico periodo =
                new PeriodoAcademico();

        periodo.setId(
                30L
        );

        periodo.setCodigo(
                "2026-02"
        );

        periodo.setNombre(
                "Segundo período 2026"
        );

        autorizacion.setIdPeriodo(
                periodo
        );

        autorizacion.setIdEstadoAutorizacion(
                crearEstado(
                        "PENDIENTE"
                )
        );

        DocumentosEstudiante comprobante =
                new DocumentosEstudiante();

        comprobante.setId(
                40L
        );

        comprobante.setNombreOriginal(
                "comprobante-matricula.pdf"
        );

        autorizacion.setIdDocumentoComprobanteMatricula(
                comprobante
        );

        autorizacion.setMensualidadesAlDia(
                true
        );

        autorizacion.setPagoMatriculaConfirmado(
                true
        );

        autorizacion.setDocumentacionCompleta(
                true
        );

        autorizacion.setEstudianteActivo(
                true
        );

        autorizacion.setDatosCompletos(
                true
        );

        return autorizacion;
    }

    private AutorizacionesMatricula
    crearAutorizacionPendienteIncompleta() {

        AutorizacionesMatricula autorizacion =
                crearAutorizacionPendienteValida();

        autorizacion.setMensualidadesAlDia(
                false
        );

        autorizacion.setPagoMatriculaConfirmado(
                false
        );

        autorizacion.setDocumentacionCompleta(
                false
        );

        autorizacion.setIdDocumentoComprobanteMatricula(
                null
        );

        return autorizacion;
    }

    private Estudiante crearEstudianteConDatosCompletos() {

        Estudiante estudiante =
                new Estudiante();

        estudiante.setId(
                20L
        );

        estudiante.setNombres(
                "Ana"
        );

        estudiante.setPrimerApellido(
                "Sánchez"
        );

        estudiante.setSegundoApellido(
                "Mora"
        );

        estudiante.setNumeroIdentificacion(
                "111110000"
        );

        estudiante.setTelefono(
                "88880000"
        );

        estudiante.setCorreo(
                "ana.sanchez@sigac.test"
        );

        estudiante.setDireccion(
                "San José, Costa Rica"
        );

        estudiante.setFechaNacimiento(
                LocalDate.of(
                        2005,
                        1,
                        1
                )
        );

        estudiante.setActivo(
                true
        );

        return estudiante;
    }

    private EstadosAutorizacionesMatricula crearEstado(
            String codigo
    ) {

        EstadosAutorizacionesMatricula estado =
                new EstadosAutorizacionesMatricula();

        estado.setCodigo(
                codigo
        );

        estado.setNombre(
                codigo
        );

        estado.setActivo(
                true
        );

        return estado;
    }

    private Usuario crearUsuarioAdministrativo() {

        Usuario usuario =
                new Usuario();

        usuario.setId(
                50L
        );

        usuario.setCedula(
                "888888888"
        );

        usuario.setNombre(
                "Usuario Administrativo"
        );

        usuario.setCorreo(
                "administrativo@sigac.test"
        );

        return usuario;
    }

    private void configurarUsuarioAutenticado() {

        Authentication authentication =
                mock(Authentication.class);

        when(
                authentication.isAuthenticated()
        ).thenReturn(
                true
        );

        when(
                authentication.getName()
        ).thenReturn(
                "888888888"
        );

        SecurityContext securityContext =
                mock(SecurityContext.class);

        when(
                securityContext.getAuthentication()
        ).thenReturn(
                authentication
        );

        SecurityContextHolder.setContext(
                securityContext
        );
    }
}