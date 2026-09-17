package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.AutorizacionesMatricula;
import cr.acusab.una.sigac.data.entity.EstadosAutorizacionesMatricula;
import cr.acusab.una.sigac.data.entity.NotificacionEstudiante;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.AutorizacionesMatriculaRepository;
import cr.acusab.una.sigac.data.repository.EstadosAutorizacionesMatriculaRepository;
import cr.acusab.una.sigac.data.repository.NotificacionEstudianteRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.request.ConfirmarMatriculaRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.AutorizacionMatriculaResponseDTO;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AutorizacionMatriculaService {

    private static final String ESTADO_PENDIENTE = "PENDIENTE";
    private static final String ESTADO_AUTORIZADA = "AUTORIZADA";
    private static final String ESTADO_RECHAZADA = "RECHAZADA";

    private static final String DECISION_APROBAR = "APROBAR";
    private static final String DECISION_RECHAZAR = "RECHAZAR";

    private static final String NOTIFICACION_APROBADA =
            "MATRICULA_APROBADA";

    private static final String NOTIFICACION_RECHAZADA =
            "MATRICULA_RECHAZADA";

    private final AutorizacionesMatriculaRepository autorizacionRepository;
    private final EstadosAutorizacionesMatriculaRepository estadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionEstudianteRepository notificacionRepository;

    public AutorizacionMatriculaService(
            AutorizacionesMatriculaRepository autorizacionRepository,
            EstadosAutorizacionesMatriculaRepository estadoRepository,
            UsuarioRepository usuarioRepository,
            NotificacionEstudianteRepository notificacionRepository
    ) {
        this.autorizacionRepository = autorizacionRepository;
        this.estadoRepository = estadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionRepository = notificacionRepository;
    }

    /*
     * ============================================================
     * HU-012 - Listar solicitudes pendientes
     * ============================================================
     */
    @Transactional(readOnly = true)
    public List<AutorizacionMatriculaResponseDTO> listarPendientes() {

        return autorizacionRepository
                .findByIdEstadoAutorizacion_CodigoIgnoreCaseOrderByCreadoEnAsc(
                        ESTADO_PENDIENTE
                )
                .stream()
                .map(this::mapearAResponse)
                .toList();
    }

    /*
     * ============================================================
     * HU-012 - Historial de solicitudes revisadas
     * ============================================================
     *
     * Incluye únicamente solicitudes que ya fueron procesadas:
     * - AUTORIZADA
     * - RECHAZADA
     *
     * El repositorio las devuelve de la más recientemente
     * revisada a la más antigua.
     */
    @Transactional(readOnly = true)
    public List<AutorizacionMatriculaResponseDTO> listarHistorial() {

        return autorizacionRepository
                .findByIdEstadoAutorizacion_CodigoInOrderByFechaRevisionDesc(
                        List.of(
                                ESTADO_AUTORIZADA,
                                ESTADO_RECHAZADA
                        )
                )
                .stream()
                .map(this::mapearAResponse)
                .toList();
    }

    /*
     * ============================================================
     * HU-012 - Aprobar o rechazar solicitud
     * ============================================================
     */
    @Transactional
    public AutorizacionMatriculaResponseDTO confirmar(
            Long idAutorizacion,
            ConfirmarMatriculaRequestDTO request
    ) {

        AutorizacionesMatricula autorizacion =
                autorizacionRepository
                        .findById(idAutorizacion)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "La solicitud de matrícula indicada no existe."
                                )
                        );

        validarEstadoPendiente(
                autorizacion
        );

        Usuario usuarioRevisor =
                obtenerUsuarioAutenticado();

        String decision =
                request.getDecision()
                        .trim()
                        .toUpperCase();

        String codigoNuevoEstado;

        if (DECISION_APROBAR.equals(decision)) {

            validarRequisitosParaAprobar(
                    autorizacion
            );

            codigoNuevoEstado =
                    ESTADO_AUTORIZADA;

        } else if (DECISION_RECHAZAR.equals(decision)) {

            codigoNuevoEstado =
                    ESTADO_RECHAZADA;

        } else {

            throw new BusinessException(
                    "La decisión debe ser APROBAR o RECHAZAR."
            );
        }

        EstadosAutorizacionesMatricula nuevoEstado =
                estadoRepository
                        .findByCodigoAndActivoTrue(
                                codigoNuevoEstado
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        "No se encontró el estado de autorización requerido."
                                )
                        );

        autorizacion.setIdEstadoAutorizacion(
                nuevoEstado
        );

        autorizacion.setIdUsuarioRevisa(
                usuarioRevisor
        );

        autorizacion.setFechaRevision(
                Instant.now()
        );

        String observaciones =
                request.getObservaciones();

        if (observaciones != null
                && !observaciones.isBlank()) {

            autorizacion.setObservaciones(
                    observaciones.trim()
            );

        } else {

            autorizacion.setObservaciones(
                    null
            );
        }

        AutorizacionesMatricula guardada =
                autorizacionRepository.saveAndFlush(
                        autorizacion
                );

        /*
         * Después de registrar correctamente la decisión,
         * se genera la notificación correspondiente al estudiante.
         */
        crearNotificacionResultado(
                guardada,
                codigoNuevoEstado
        );

        return mapearAResponse(
                guardada
        );
    }

    /*
     * ============================================================
     * Validar que la solicitud siga pendiente
     * ============================================================
     */
    private void validarEstadoPendiente(
            AutorizacionesMatricula autorizacion
    ) {

        if (autorizacion.getIdEstadoAutorizacion() == null
                || autorizacion
                .getIdEstadoAutorizacion()
                .getCodigo() == null
                || !ESTADO_PENDIENTE.equalsIgnoreCase(
                autorizacion
                        .getIdEstadoAutorizacion()
                        .getCodigo()
        )) {

            throw new BusinessException(
                    "La solicitud de matrícula ya fue procesada."
            );
        }
    }

    /*
     * ============================================================
     * Validaciones necesarias para aprobar
     * ============================================================
     *
     * Una solicitud solamente puede autorizarse cuando:
     * - las mensualidades están al día;
     * - el pago de matrícula está confirmado;
     * - la documentación está completa;
     * - existe comprobante de matrícula.
     */
    private void validarRequisitosParaAprobar(
            AutorizacionesMatricula autorizacion
    ) {

        if (!Boolean.TRUE.equals(
                autorizacion.getMensualidadesAlDia()
        )) {

            throw new BusinessException(
                    "No se puede aprobar la matrícula porque las mensualidades no están al día."
            );
        }

        if (!Boolean.TRUE.equals(
                autorizacion.getPagoMatriculaConfirmado()
        )) {

            throw new BusinessException(
                    "No se puede aprobar la matrícula porque el pago de matrícula no está confirmado."
            );
        }

        if (!Boolean.TRUE.equals(
                autorizacion.getDocumentacionCompleta()
        )) {

            throw new BusinessException(
                    "No se puede aprobar la matrícula porque la documentación no está completa."
            );
        }

        if (autorizacion.getIdDocumentoComprobanteMatricula() == null) {

            throw new BusinessException(
                    "No se puede aprobar la matrícula porque no existe un comprobante de matrícula."
            );
        }
    }

    /*
     * ============================================================
     * Obtener usuario administrativo autenticado
     * ============================================================
     */
    private Usuario obtenerUsuarioAutenticado() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {

            throw new BusinessException(
                    "No se pudo identificar al usuario autenticado."
            );
        }

        String cedula =
                authentication.getName();

        return usuarioRepository
                .findByCedula(
                        cedula
                )
                .orElseThrow(() ->
                        new BusinessException(
                                "No se encontró el usuario administrativo autenticado."
                        )
                );
    }

    /*
     * ============================================================
     * Crear notificación para el estudiante
     * ============================================================
     */
    private void crearNotificacionResultado(
            AutorizacionesMatricula autorizacion,
            String codigoEstado
    ) {

        NotificacionEstudiante notificacion =
                new NotificacionEstudiante();

        notificacion.setIdEstudiante(
                autorizacion.getIdEstudiante()
        );

        notificacion.setIdAutorizacion(
                autorizacion
        );

        boolean aprobada =
                ESTADO_AUTORIZADA.equals(
                        codigoEstado
                );

        if (aprobada) {

            notificacion.setTipo(
                    NOTIFICACION_APROBADA
            );

            notificacion.setAsunto(
                    "Solicitud de matrícula aprobada"
            );

        } else {

            notificacion.setTipo(
                    NOTIFICACION_RECHAZADA
            );

            notificacion.setAsunto(
                    "Solicitud de matrícula rechazada"
            );
        }

        String nombrePeriodo =
                obtenerNombrePeriodo(
                        autorizacion
                );

        String mensaje;

        if (aprobada) {

            mensaje =
                    "Su solicitud de matrícula para "
                            + nombrePeriodo
                            + " fue aprobada.";

        } else {

            mensaje =
                    "Su solicitud de matrícula para "
                            + nombrePeriodo
                            + " fue rechazada.";
        }

        if (autorizacion.getObservaciones() != null
                && !autorizacion
                .getObservaciones()
                .isBlank()) {

            mensaje +=
                    " Observaciones administrativas: "
                            + autorizacion.getObservaciones();
        }

        notificacion.setMensaje(
                mensaje
        );

        notificacion.setLeida(
                false
        );

        notificacionRepository.save(
                notificacion
        );
    }

    /*
     * ============================================================
     * Obtener descripción del período
     * ============================================================
     */
    private String obtenerNombrePeriodo(
            AutorizacionesMatricula autorizacion
    ) {

        if (autorizacion.getIdPeriodo() == null) {
            return "el período académico correspondiente";
        }

        if (autorizacion
                .getIdPeriodo()
                .getNombre() != null
                && !autorizacion
                .getIdPeriodo()
                .getNombre()
                .isBlank()) {

            return autorizacion
                    .getIdPeriodo()
                    .getNombre();
        }

        if (autorizacion
                .getIdPeriodo()
                .getCodigo() != null
                && !autorizacion
                .getIdPeriodo()
                .getCodigo()
                .isBlank()) {

            return autorizacion
                    .getIdPeriodo()
                    .getCodigo();
        }

        return "el período académico correspondiente";
    }

    /*
     * ============================================================
     * Conversión de entidad a DTO
     * ============================================================
     */
    private AutorizacionMatriculaResponseDTO mapearAResponse(
            AutorizacionesMatricula autorizacion
    ) {

        AutorizacionMatriculaResponseDTO response =
                new AutorizacionMatriculaResponseDTO();

        response.setIdAutorizacion(
                autorizacion.getId()
        );

        if (autorizacion.getIdEstudiante() != null) {

            response.setIdEstudiante(
                    autorizacion
                            .getIdEstudiante()
                            .getId()
            );

            String nombre =
                    autorizacion
                            .getIdEstudiante()
                            .getNombres();

            String primerApellido =
                    autorizacion
                            .getIdEstudiante()
                            .getPrimerApellido();

            String segundoApellido =
                    autorizacion
                            .getIdEstudiante()
                            .getSegundoApellido();

            String nombreCompleto =
                    unirNombre(
                            nombre,
                            primerApellido,
                            segundoApellido
                    );

            response.setEstudiante(
                    nombreCompleto
            );
        }

        if (autorizacion.getIdPeriodo() != null) {

            response.setIdPeriodo(
                    autorizacion
                            .getIdPeriodo()
                            .getId()
            );

            response.setCodigoPeriodo(
                    autorizacion
                            .getIdPeriodo()
                            .getCodigo()
            );

            response.setPeriodo(
                    autorizacion
                            .getIdPeriodo()
                            .getNombre()
            );
        }

        if (autorizacion.getIdEstadoAutorizacion() != null) {

            response.setEstado(
                    autorizacion
                            .getIdEstadoAutorizacion()
                            .getCodigo()
            );
        }

        response.setMensualidadesAlDia(
                autorizacion.getMensualidadesAlDia()
        );

        response.setPagoMatriculaConfirmado(
                autorizacion.getPagoMatriculaConfirmado()
        );

        response.setDocumentacionCompleta(
                autorizacion.getDocumentacionCompleta()
        );

        if (autorizacion
                .getIdDocumentoComprobanteMatricula() != null) {

            response.setIdDocumentoComprobanteMatricula(
                    autorizacion
                            .getIdDocumentoComprobanteMatricula()
                            .getId()
            );

            response.setComprobanteMatricula(
                    autorizacion
                            .getIdDocumentoComprobanteMatricula()
                            .getNombreOriginal()
            );
        }

        response.setFechaRevision(
                autorizacion.getFechaRevision()
        );

        if (autorizacion.getIdUsuarioRevisa() != null) {

            response.setRevisadoPor(
                    autorizacion
                            .getIdUsuarioRevisa()
                            .getCorreo()
            );
        }

        response.setObservaciones(
                autorizacion.getObservaciones()
        );

        return response;
    }

    /*
     * Une de forma segura los componentes del nombre.
     */
    private String unirNombre(
            String nombres,
            String primerApellido,
            String segundoApellido
    ) {

        StringBuilder nombreCompleto =
                new StringBuilder();

        agregarParteNombre(
                nombreCompleto,
                nombres
        );

        agregarParteNombre(
                nombreCompleto,
                primerApellido
        );

        agregarParteNombre(
                nombreCompleto,
                segundoApellido
        );

        return nombreCompleto
                .toString()
                .trim();
    }

    private void agregarParteNombre(
            StringBuilder nombreCompleto,
            String parte
    ) {

        if (parte != null
                && !parte.isBlank()) {

            if (!nombreCompleto.isEmpty()) {
                nombreCompleto.append(" ");
            }

            nombreCompleto.append(
                    parte.trim()
            );
        }
    }
}