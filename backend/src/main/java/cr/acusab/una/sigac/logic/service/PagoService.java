package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.AutorizacionesMatricula;
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
import cr.acusab.una.sigac.presentation.dto.request.RegistrarPagoRequest;
import cr.acusab.una.sigac.presentation.dto.request.RegistrarPagosRequest;
import cr.acusab.una.sigac.presentation.dto.response.EstadoMesPagoResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstadoPeriodoPagoResponse;
import cr.acusab.una.sigac.presentation.dto.response.PagoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/*
 * HU (Registrar pago) - Administracion Academica registra directamente
 * el pago de un estudiante (sin depender de que el estudiante suba
 * nada) y adjunta el comprobante como evidencia. Reutiliza la tabla
 * mensualidades_estudiantes, que tambien alimentara la consulta de
 * montos a pagar del estudiante (HU-019).
 */
@Service
public class PagoService {

    private static final long MAX_TAMANO_COMPROBANTE = 15L * 1024L * 1024L;
    private static final Set<String> TIPOS_MIME_PERMITIDOS = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp"
    );
    private static final String CODIGO_TIPO_DOCUMENTO_COMPROBANTE = "COMPROBANTE_MENSUALIDAD";
    private static final String CODIGO_TIPO_DOCUMENTO_MATRICULA = "COMPROBANTE_MATRICULA";
    private static final String ESTADO_AUTORIZACION_PENDIENTE = "PENDIENTE";
    private static final Locale ESPANOL = Locale.forLanguageTag("es-CR");

    private final MensualidadEstudianteRepository mensualidadEstudianteRepository;
    private final EstudianteRepository estudianteRepository;
    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final BecaRepository becaRepository;
    private final MensualidadCalculoService mensualidadCalculoService;
    private final DocumentoEstudianteRepository documentoEstudianteRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutorizacionesMatriculaRepository autorizacionesMatriculaRepository;
    private final AutorizacionMatriculaService autorizacionMatriculaService;

    @Value("${sigac.storage.documents:./storage/documents}")
    private String storageDirectory;

    public PagoService(
            MensualidadEstudianteRepository mensualidadEstudianteRepository,
            EstudianteRepository estudianteRepository,
            PeriodoAcademicoRepository periodoAcademicoRepository,
            BecaRepository becaRepository,
            MensualidadCalculoService mensualidadCalculoService,
            DocumentoEstudianteRepository documentoEstudianteRepository,
            TipoDocumentoRepository tipoDocumentoRepository,
            UsuarioRepository usuarioRepository,
            AutorizacionesMatriculaRepository autorizacionesMatriculaRepository,
            AutorizacionMatriculaService autorizacionMatriculaService
    ) {
        this.mensualidadEstudianteRepository = mensualidadEstudianteRepository;
        this.estudianteRepository = estudianteRepository;
        this.periodoAcademicoRepository = periodoAcademicoRepository;
        this.becaRepository = becaRepository;
        this.mensualidadCalculoService = mensualidadCalculoService;
        this.documentoEstudianteRepository = documentoEstudianteRepository;
        this.tipoDocumentoRepository = tipoDocumentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.autorizacionesMatriculaRepository = autorizacionesMatriculaRepository;
        this.autorizacionMatriculaService = autorizacionMatriculaService;
    }

    @Transactional
    public PagoResponse registrar(RegistrarPagoRequest request, MultipartFile comprobante) {

        Estudiante estudiante = estudianteRepository.findById(request.getIdEstudiante())
                .orElseThrow(() -> new BusinessException("El estudiante indicado no existe."));

        if (!Boolean.TRUE.equals(estudiante.getActivo())) {
            throw new BusinessException("El estudiante indicado se encuentra inactivo.");
        }

        PeriodoAcademico periodo = periodoAcademicoRepository.findById(request.getIdPeriodo())
                .orElseThrow(() -> new BusinessException("El período académico indicado no existe."));

        if (request.getFechaPago().isAfter(LocalDate.now())) {
            throw new BusinessException("La fecha de pago no puede ser una fecha futura.");
        }

        validarFechasDelPeriodo(periodo, request.getAnio(), request.getMes(), request.getFechaPago());

        // Estudiante becado al 100%: no tiene pagos pendientes para este período.
        Beca becaActiva = becaRepository
                .findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(estudiante.getId(), periodo.getId())
                .orElse(null);

        if (becaActiva != null && becaActiva.getPorcentaje().compareTo(BigDecimal.valueOf(100)) == 0) {
            throw new BusinessException(
                    "El estudiante tiene una beca del 100% para este período académico; no tiene pagos pendientes.");
        }

        validarComprobante(comprobante);

        Integer anio = request.getAnio();
        Short mes = request.getMes();

        MensualidadesEstudiante mensualidad = mensualidadEstudianteRepository
                .findByIdEstudiante_IdAndAnioAndMes(estudiante.getId(), anio, mes)
                .orElseGet(MensualidadesEstudiante::new);

        if (Boolean.TRUE.equals(mensualidad.getPagada())) {
            throw new BusinessException(
                    "Ya existe un pago registrado para este estudiante en ese mes.");
        }

        Usuario usuarioRegistra = obtenerUsuarioAutenticado();
        TiposDocumento tipoComprobante = tipoDocumento(CODIGO_TIPO_DOCUMENTO_COMPROBANTE);

        mensualidad = registrarMes(mensualidad, estudiante, periodo, becaActiva, anio, mes,
                request.getFechaPago(), normalizarNullable(request.getObservaciones()),
                comprobante, tipoComprobante, usuarioRegistra);

        return construirRespuesta(mensualidad, estudiante, periodo, becaActiva,
                "Pago registrado correctamente.");
    }

    /*
     * Registra con un solo comprobante varios pagos del mismo período:
     * los meses marcados y, si se indica, el pago de matrícula. Todo o
     * nada: si un pago no es válido, no se registra ninguno.
     */
    @Transactional
    public EstadoPeriodoPagoResponse registrarVarios(RegistrarPagosRequest request, MultipartFile comprobante) {

        Estudiante estudiante = estudianteRepository.findById(request.getIdEstudiante())
                .orElseThrow(() -> new BusinessException("El estudiante indicado no existe."));

        if (!Boolean.TRUE.equals(estudiante.getActivo())) {
            throw new BusinessException("El estudiante indicado se encuentra inactivo.");
        }

        PeriodoAcademico periodo = periodoAcademicoRepository.findById(request.getIdPeriodo())
                .orElseThrow(() -> new BusinessException("El período académico indicado no existe."));

        List<RegistrarPagosRequest.MesPago> meses =
                request.getMeses() == null ? List.of() : request.getMeses();

        if (meses.isEmpty() && !request.isIncluirMatricula()) {
            throw new BusinessException("Seleccione al menos un mes o el pago de matrícula.");
        }

        if (request.getFechaPago().isAfter(LocalDate.now())) {
            throw new BusinessException("La fecha de pago no puede ser una fecha futura.");
        }

        validarFechasDelPeriodo(periodo, null, null, request.getFechaPago());

        Set<YearMonth> vistos = new HashSet<>();
        for (RegistrarPagosRequest.MesPago mesPago : meses) {
            validarFechasDelPeriodo(periodo, mesPago.getAnio(), mesPago.getMes(), request.getFechaPago());
            if (!vistos.add(YearMonth.of(mesPago.getAnio(), mesPago.getMes()))) {
                throw new BusinessException("Un mes se indicó más de una vez.");
            }
        }

        Beca becaActiva = becaRepository
                .findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(estudiante.getId(), periodo.getId())
                .orElse(null);

        if (!meses.isEmpty() && becaActiva != null
                && becaActiva.getPorcentaje().compareTo(BigDecimal.valueOf(100)) == 0) {
            throw new BusinessException(
                    "El estudiante tiene una beca del 100% para este período académico; sus mensualidades no se cobran.");
        }

        List<MensualidadesEstudiante> mensualidades = new ArrayList<>();
        for (RegistrarPagosRequest.MesPago mesPago : meses) {
            MensualidadesEstudiante mensualidad = mensualidadEstudianteRepository
                    .findByIdEstudiante_IdAndAnioAndMes(estudiante.getId(), mesPago.getAnio(), mesPago.getMes())
                    .orElseGet(MensualidadesEstudiante::new);
            if (Boolean.TRUE.equals(mensualidad.getPagada())) {
                throw new BusinessException("Ya existe un pago registrado para "
                        + nombreMes(mesPago.getAnio(), mesPago.getMes()) + ".");
            }
            mensualidades.add(mensualidad);
        }

        if (request.isIncluirMatricula()) {
            autorizacionMatriculaService.validarPuedeRegistrarPagoMatricula(estudiante.getId(), periodo.getId());
        }

        validarComprobante(comprobante);

        Usuario usuarioRegistra = obtenerUsuarioAutenticado();
        String observaciones = normalizarNullable(request.getObservaciones());

        if (!meses.isEmpty()) {
            TiposDocumento tipoMensualidad = tipoDocumento(CODIGO_TIPO_DOCUMENTO_COMPROBANTE);
            // Cada mes guarda su propia copia del comprobante: un documento
            // solo puede pertenecer a una mensualidad (uq_mensualidades_comprobante).
            for (int i = 0; i < meses.size(); i++) {
                registrarMes(mensualidades.get(i), estudiante, periodo, becaActiva,
                        meses.get(i).getAnio(), meses.get(i).getMes(), request.getFechaPago(),
                        observaciones, comprobante, tipoMensualidad, usuarioRegistra);
            }
        }

        if (request.isIncluirMatricula()) {
            TiposDocumento tipoMatricula = tipoDocumento(CODIGO_TIPO_DOCUMENTO_MATRICULA);
            DocumentosEstudiante documento = guardarComprobante(estudiante, comprobante, tipoMatricula, usuarioRegistra);
            // La fecha de pago de la matrícula se guarda como fecha del comprobante
            documento.setFechaEmision(request.getFechaPago());
            documento = documentoEstudianteRepository.save(documento);
            autorizacionMatriculaService.registrarPagoMatricula(estudiante, periodo, documento, observaciones);
        }

        return consultarEstadoPeriodo(estudiante.getId(), periodo.getId());
    }

    /*
     * Estado de pagos de todo el período: la matrícula y cada mes.
     */
    @Transactional(readOnly = true)
    public EstadoPeriodoPagoResponse consultarEstadoPeriodo(Long idEstudiante, Long idPeriodo) {

        Estudiante estudiante = estudianteRepository.findById(idEstudiante)
                .orElseThrow(() -> new BusinessException("El estudiante indicado no existe."));

        PeriodoAcademico periodo = periodoAcademicoRepository.findById(idPeriodo)
                .orElseThrow(() -> new BusinessException("El período académico indicado no existe."));

        Beca becaActiva = becaRepository
                .findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(estudiante.getId(), periodo.getId())
                .orElse(null);

        BigDecimal montoBase = mensualidadCalculoService.calcularMontoBase(estudiante.getId());
        BigDecimal porcentajeBeca = becaActiva != null ? becaActiva.getPorcentaje() : null;
        boolean becado100 = porcentajeBeca != null && porcentajeBeca.compareTo(BigDecimal.valueOf(100)) == 0;
        BigDecimal montoEsperado = becado100
                ? BigDecimal.ZERO.setScale(2)
                : mensualidadCalculoService.calcularMontoNeto(montoBase, porcentajeBeca);

        EstadoPeriodoPagoResponse response = new EstadoPeriodoPagoResponse();
        response.setIdPeriodo(periodo.getId());
        response.setMontoEsperado(montoEsperado);
        response.setPorcentajeBeca(porcentajeBeca);
        response.setSinPagosPendientes(becado100);

        for (YearMonth mesPeriodo : mesesDelPeriodo(periodo)) {
            Short mes = (short) mesPeriodo.getMonthValue();
            MensualidadesEstudiante mensualidad = mensualidadEstudianteRepository
                    .findByIdEstudiante_IdAndAnioAndMes(idEstudiante, mesPeriodo.getYear(), mes)
                    .orElse(null);

            EstadoMesPagoResponse estadoMes = new EstadoMesPagoResponse();
            estadoMes.setAnio(mesPeriodo.getYear());
            estadoMes.setMes(mes);
            estadoMes.setPorcentajeBeca(porcentajeBeca);
            estadoMes.setSinPagosPendientes(becado100);
            estadoMes.setMontoEsperado(montoEsperado);

            boolean pagada = mensualidad != null && Boolean.TRUE.equals(mensualidad.getPagada());
            estadoMes.setPagada(pagada);
            if (pagada) {
                estadoMes.setIdMensualidad(mensualidad.getId());
                estadoMes.setMonto(mensualidad.getMonto());
                estadoMes.setFechaPago(mensualidad.getFechaPago());
                estadoMes.setObservaciones(mensualidad.getObservaciones());
            }
            response.getMeses().add(estadoMes);
        }

        autorizacionesMatriculaRepository
                .findByIdEstudiante_IdAndIdPeriodo_Id(idEstudiante, idPeriodo)
                .filter(autorizacion -> autorizacion.getIdDocumentoComprobanteMatricula() != null)
                .ifPresent(autorizacion -> {
                    EstadoPeriodoPagoResponse.EstadoMatriculaPago matricula = response.getMatricula();
                    matricula.setPagada(true);
                    matricula.setIdAutorizacion(autorizacion.getId());
                    matricula.setFechaPago(autorizacion.getIdDocumentoComprobanteMatricula().getFechaEmision());
                    matricula.setEliminable(autorizacion.getIdEstadoAutorizacion() != null
                            && ESTADO_AUTORIZACION_PENDIENTE.equalsIgnoreCase(
                                    autorizacion.getIdEstadoAutorizacion().getCodigo()));
                });

        return response;
    }

    /*
     * Comprobante del pago de matrícula de un período (guardado en la
     * solicitud de matrícula de ese período).
     */
    @Transactional(readOnly = true)
    public ComprobanteArchivo obtenerComprobanteMatricula(Long idAutorizacion) {
        AutorizacionesMatricula autorizacion = autorizacionesMatriculaRepository.findById(idAutorizacion)
                .orElseThrow(() -> new BusinessException("El pago de matrícula indicado no existe."));

        DocumentosEstudiante documento = autorizacion.getIdDocumentoComprobanteMatricula();
        if (documento == null) {
            throw new BusinessException("Este pago de matrícula no tiene comprobante adjunto.");
        }
        return new ComprobanteArchivo(
                documento.getUbicacionArchivo(),
                documento.getTipoMime(),
                documento.getNombreOriginal()
        );
    }

    /*
     * Elimina el pago de matrícula registrado por error (solo mientras la
     * solicitud de matrícula siga pendiente), con su comprobante.
     */
    @Transactional
    public void eliminarPagoMatricula(Long idAutorizacion) {
        DocumentosEstudiante documento = autorizacionMatriculaService.quitarPagoMatricula(idAutorizacion);
        try {
            Files.deleteIfExists(Paths.get(documento.getUbicacionArchivo()));
        } catch (IOException ignored) {
            // El registro igual se limpia aunque el archivo físico no se pueda borrar.
        }
        documentoEstudianteRepository.delete(documento);
    }

    // Guarda el comprobante y deja la mensualidad pagada con el monto calculado
    private MensualidadesEstudiante registrarMes(
            MensualidadesEstudiante mensualidad,
            Estudiante estudiante,
            PeriodoAcademico periodo,
            Beca becaActiva,
            Integer anio,
            Short mes,
            LocalDate fechaPago,
            String observaciones,
            MultipartFile comprobante,
            TiposDocumento tipoComprobante,
            Usuario usuarioRegistra
    ) {
        DocumentosEstudiante documento = guardarComprobante(estudiante, comprobante, tipoComprobante, usuarioRegistra);

        // El monto nunca lo digita Administracion: se calcula segun la
        // mensualidad del estudiante y el descuento de su beca activa,
        // para evitar que se registre un monto incorrecto.
        BigDecimal montoBase = mensualidadCalculoService.calcularMontoBase(estudiante.getId());
        BigDecimal montoAPagar = mensualidadCalculoService.calcularMontoNeto(
                montoBase, becaActiva != null ? becaActiva.getPorcentaje() : null);

        Instant ahora = Instant.now();

        mensualidad.setIdEstudiante(estudiante);
        mensualidad.setIdPeriodo(periodo);
        mensualidad.setAnio(anio);
        mensualidad.setMes(mes);
        mensualidad.setMonto(montoAPagar);
        mensualidad.setFechaPago(fechaPago);
        mensualidad.setIdDocumentoComprobante(documento);
        mensualidad.setPagada(true);
        mensualidad.setFechaConfirmacion(ahora);
        mensualidad.setIdUsuarioConfirma(usuarioRegistra);
        mensualidad.setObservaciones(observaciones);

        return mensualidadEstudianteRepository.save(mensualidad);
    }

    private TiposDocumento tipoDocumento(String codigo) {
        return tipoDocumentoRepository
                .findByCodigoAndActivoTrue(codigo)
                .orElseThrow(() -> new BusinessException(
                        "No está configurado el tipo de documento " + codigo));
    }

    // Meses (año + mes) comprendidos en el período académico
    private List<YearMonth> mesesDelPeriodo(PeriodoAcademico periodo) {
        List<YearMonth> meses = new ArrayList<>();
        if (periodo.getFechaInicio() == null || periodo.getFechaFin() == null) return meses;
        YearMonth limite = YearMonth.from(periodo.getFechaFin());
        for (YearMonth mes = YearMonth.from(periodo.getFechaInicio()); !mes.isAfter(limite); mes = mes.plusMonths(1)) {
            meses.add(mes);
        }
        return meses;
    }

    private String nombreMes(Integer anio, Short mes) {
        return YearMonth.of(anio, mes).getMonth().getDisplayName(TextStyle.FULL, ESPANOL) + " " + anio;
    }

    /*
     * Estado del pago de un estudiante para un mes especifico dentro de
     * un periodo academico: si ya esta pagado (y con que monto y fecha)
     * o si esta pendiente (y cuanto corresponde pagar, ya con el
     * descuento de beca aplicado).
     */
    @Transactional(readOnly = true)
    public EstadoMesPagoResponse consultarEstadoMes(Long idEstudiante, Long idPeriodo, Integer anio, Short mes) {

        Estudiante estudiante = estudianteRepository.findById(idEstudiante)
                .orElseThrow(() -> new BusinessException("El estudiante indicado no existe."));

        PeriodoAcademico periodo = periodoAcademicoRepository.findById(idPeriodo)
                .orElseThrow(() -> new BusinessException("El período académico indicado no existe."));

        Beca becaActiva = becaRepository
                .findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(estudiante.getId(), periodo.getId())
                .orElse(null);

        BigDecimal montoBase = mensualidadCalculoService.calcularMontoBase(estudiante.getId());
        BigDecimal porcentajeBeca = becaActiva != null ? becaActiva.getPorcentaje() : null;
        BigDecimal montoEsperado = mensualidadCalculoService.calcularMontoNeto(montoBase, porcentajeBeca);

        boolean becado100 = porcentajeBeca != null && porcentajeBeca.compareTo(BigDecimal.valueOf(100)) == 0;

        MensualidadesEstudiante mensualidad = mensualidadEstudianteRepository
                .findByIdEstudiante_IdAndAnioAndMes(idEstudiante, anio, mes)
                .orElse(null);

        EstadoMesPagoResponse response = new EstadoMesPagoResponse();
        response.setAnio(anio);
        response.setMes(mes);
        response.setPorcentajeBeca(porcentajeBeca);
        response.setSinPagosPendientes(becado100);

        boolean pagada = mensualidad != null && Boolean.TRUE.equals(mensualidad.getPagada());
        response.setPagada(pagada);

        if (pagada) {
            response.setIdMensualidad(mensualidad.getId());
            response.setMonto(mensualidad.getMonto());
            response.setFechaPago(mensualidad.getFechaPago());
            response.setObservaciones(mensualidad.getObservaciones());
        }

        response.setMontoEsperado(becado100 ? BigDecimal.ZERO.setScale(2) : montoEsperado);

        return response;
    }

    /*
     * Comprobante adjunto a un pago ya registrado, para poder verlo
     * (ej. confirmar que no se subió el archivo equivocado).
     *
     * Se extraen los datos dentro de la transaccion (en vez de devolver
     * la entidad tal cual) porque id_documento_comprobante es una
     * relacion "lazy": si se devuelve la entidad y se leen sus campos
     * despues, con la sesion de Hibernate ya cerrada (open-in-view esta
     * desactivado), truena con LazyInitializationException.
     */
    @Transactional(readOnly = true)
    public ComprobanteArchivo obtenerComprobante(Long idMensualidad) {
        MensualidadesEstudiante mensualidad = mensualidadEstudianteRepository.findById(idMensualidad)
                .orElseThrow(() -> new BusinessException("El pago indicado no existe."));

        DocumentosEstudiante documento = mensualidad.getIdDocumentoComprobante();
        if (documento == null) {
            throw new BusinessException("Este pago no tiene comprobante adjunto.");
        }
        return new ComprobanteArchivo(
                documento.getUbicacionArchivo(),
                documento.getTipoMime(),
                documento.getNombreOriginal()
        );
    }

    public record ComprobanteArchivo(String rutaArchivo, String tipoMime, String nombreOriginal) {}

    /*
     * Elimina un pago ya registrado (y su comprobante), dejando el mes
     * nuevamente pendiente. Es la forma de "modificar" un pago: se
     * elimina el que se subió por error y se vuelve a registrar.
     */
    @Transactional
    public void eliminarPago(Long idMensualidad) {

        MensualidadesEstudiante mensualidad = mensualidadEstudianteRepository.findById(idMensualidad)
                .orElseThrow(() -> new BusinessException("El pago indicado no existe."));

        DocumentosEstudiante documento = mensualidad.getIdDocumentoComprobante();

        mensualidadEstudianteRepository.delete(mensualidad);

        if (documento != null) {
            try {
                Files.deleteIfExists(Paths.get(documento.getUbicacionArchivo()));
            } catch (IOException ignored) {
                // El registro igual se limpia aunque el archivo físico no se pueda borrar.
            }
            documentoEstudianteRepository.delete(documento);
        }
    }

    @Transactional(readOnly = true)
    public List<PagoResponse> listarHistorial(Long idEstudiante) {

        Estudiante estudiante = estudianteRepository.findById(idEstudiante)
                .orElseThrow(() -> new BusinessException("El estudiante indicado no existe."));

        return mensualidadEstudianteRepository
                .findByIdEstudiante_IdOrderByAnioDescMesDesc(idEstudiante)
                .stream()
                .filter(m -> Boolean.TRUE.equals(m.getPagada()))
                .map(m -> construirRespuesta(
                        m,
                        estudiante,
                        m.getIdPeriodo(),
                        m.getIdPeriodo() == null ? null : becaRepository
                                .findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(idEstudiante, m.getIdPeriodo().getId())
                                .orElse(null),
                        null
                ))
                .toList();
    }

    private PagoResponse construirRespuesta(
            MensualidadesEstudiante mensualidad,
            Estudiante estudiante,
            PeriodoAcademico periodo,
            Beca becaActiva,
            String mensaje
    ) {
        BigDecimal montoBase = mensualidadCalculoService.calcularMontoBase(estudiante.getId());
        BigDecimal porcentajeBeca = becaActiva != null ? becaActiva.getPorcentaje() : null;
        BigDecimal montoEsperado = mensualidadCalculoService.calcularMontoNeto(montoBase, porcentajeBeca);

        PagoResponse response = new PagoResponse();
        response.setIdMensualidad(mensualidad.getId());
        response.setIdEstudiante(estudiante.getId());
        response.setNombreEstudiante(nombreCompleto(estudiante));

        if (periodo != null) {
            response.setIdPeriodo(periodo.getId());
            response.setCodigoPeriodo(periodo.getCodigo());
        }

        response.setAnio(mensualidad.getAnio());
        response.setMes(mensualidad.getMes());
        response.setMonto(mensualidad.getMonto());
        response.setFechaPago(mensualidad.getFechaPago());
        response.setMontoEsperado(montoEsperado);
        response.setPagada(mensualidad.getPagada());
        response.setMensaje(mensaje);

        return response;
    }

    private String nombreCompleto(Estudiante estudiante) {
        return String.join(
                " ",
                estudiante.getNombres(),
                estudiante.getPrimerApellido(),
                estudiante.getSegundoApellido() != null ? estudiante.getSegundoApellido() : ""
        ).trim().replaceAll("\\s+", " ");
    }

    private void validarComprobante(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("Debe adjuntar el comprobante de pago.");
        }
        if (archivo.getSize() > MAX_TAMANO_COMPROBANTE) {
            throw new BusinessException("El comprobante no puede superar 15 MB.");
        }
        String tipo = Objects.toString(archivo.getContentType(), "").toLowerCase(Locale.ROOT);
        if (!TIPOS_MIME_PERMITIDOS.contains(tipo)) {
            throw new BusinessException("El comprobante debe ser un PDF o una imagen (JPG, PNG o WEBP).");
        }

        // El tipo que declara el navegador se puede falsear: se revisan los
        // primeros bytes del archivo para confirmar que su contenido coincide.
        byte[] inicio;
        try (InputStream input = archivo.getInputStream()) {
            inicio = input.readNBytes(12);
        } catch (IOException e) {
            throw new BusinessException("No fue posible leer el comprobante.");
        }
        if (!contenidoCoincide(tipo, inicio)) {
            throw new BusinessException(
                    "El contenido del comprobante no corresponde a un PDF o una imagen JPG, PNG o WEBP válida.");
        }
    }

    // Firmas de archivo: PDF "%PDF-", JPG FF D8 FF, PNG 89 50 4E 47, WEBP "RIFF....WEBP"
    private boolean contenidoCoincide(String tipoMime, byte[] b) {
        return switch (tipoMime) {
            case "application/pdf" -> empiezaCon(b, 0, '%', 'P', 'D', 'F', '-');
            case "image/jpeg" -> empiezaCon(b, 0, 0xFF, 0xD8, 0xFF);
            case "image/png" -> empiezaCon(b, 0, 0x89, 0x50, 0x4E, 0x47);
            case "image/webp" -> empiezaCon(b, 0, 'R', 'I', 'F', 'F') && empiezaCon(b, 8, 'W', 'E', 'B', 'P');
            default -> false;
        };
    }

    private boolean empiezaCon(byte[] datos, int desde, int... firma) {
        if (datos.length < desde + firma.length) return false;
        for (int i = 0; i < firma.length; i++) {
            if ((datos[desde + i] & 0xFF) != firma[i]) return false;
        }
        return true;
    }

    /*
     * El mes pagado debe estar dentro del período, y la fecha de pago no
     * puede ser anterior al inicio de la matrícula (o al inicio del período
     * si no tiene fechas de matrícula).
     */
    private void validarFechasDelPeriodo(PeriodoAcademico periodo, Integer anio, Short mes, LocalDate fechaPago) {
        if (anio != null && mes != null && periodo.getFechaInicio() != null && periodo.getFechaFin() != null) {
            if (mes < 1 || mes > 12) {
                throw new BusinessException("El mes indicado no es válido.");
            }
            YearMonth pagado = YearMonth.of(anio, mes);
            if (pagado.isBefore(YearMonth.from(periodo.getFechaInicio()))
                    || pagado.isAfter(YearMonth.from(periodo.getFechaFin()))) {
                throw new BusinessException("El mes pagado no está dentro del período académico seleccionado.");
            }
        }

        LocalDate desde = periodo.getFechaInicioMatricula() != null
                ? periodo.getFechaInicioMatricula()
                : periodo.getFechaInicio();
        if (fechaPago != null && desde != null && fechaPago.isBefore(desde)) {
            throw new BusinessException("La fecha de pago no puede ser anterior al "
                    + (periodo.getFechaInicioMatricula() != null ? "inicio de la matrícula" : "inicio del período")
                    + " (" + desde + ").");
        }
    }

    private DocumentosEstudiante guardarComprobante(
            Estudiante estudiante,
            MultipartFile archivo,
            TiposDocumento tipoDocumento,
            Usuario usuarioCarga
    ) {
        Path studentDir = Paths.get(storageDirectory).toAbsolutePath().normalize()
                .resolve(String.valueOf(estudiante.getId()));

        String extension = extensionSegunTipoMime(archivo.getContentType());
        String storedName = UUID.randomUUID() + extension;
        Path destination = studentDir.resolve(storedName).normalize();

        if (!destination.startsWith(studentDir)) {
            throw new BusinessException("Ruta de almacenamiento inválida.");
        }

        try {
            Files.createDirectories(studentDir);
            Files.copy(archivo.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            DocumentosEstudiante documento = new DocumentosEstudiante();
            documento.setEstudiante(estudiante);
            documento.setIdTipoDocumento(tipoDocumento);
            documento.setNombreOriginal(archivo.getOriginalFilename());
            documento.setNombreAlmacenado(storedName);
            documento.setUbicacionArchivo(destination.toString());
            documento.setTipoMime(archivo.getContentType());
            documento.setTamanoBytes(archivo.getSize());
            documento.setSha256(sha256(destination));
            documento.setEsActual(true);
            documento.setIdUsuarioCarga(usuarioCarga);
            documento.setCargadoEn(Instant.now());

            return documentoEstudianteRepository.save(documento);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException ignored) {
                // best effort cleanup
            }
            throw new BusinessException("No fue posible almacenar el comprobante.");
        }
    }

    private String extensionSegunTipoMime(String tipoMime) {
        if (tipoMime == null) return "";
        return switch (tipoMime.toLowerCase(Locale.ROOT)) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) > 0) digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IOException("No fue posible calcular SHA-256", e);
        }
    }

    private Usuario obtenerUsuarioAutenticado() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {
            throw new IllegalStateException("No se pudo identificar al usuario autenticado.");
        }

        String cedula = authentication.getName();

        return usuarioRepository.findByCedula(cedula)
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario autenticado no existe en el sistema."));
    }

    private String normalizarNullable(String value) {
        if (value == null) return null;
        String normalizado = value.trim().replaceAll("\\s+", " ");
        return normalizado.isBlank() ? null : normalizado;
    }
}
