package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Beca;
import cr.acusab.una.sigac.data.entity.DocumentosEstudiante;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.MensualidadesEstudiante;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.TiposDocumento;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.BecaRepository;
import cr.acusab.una.sigac.data.repository.DocumentoEstudianteRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.MensualidadEstudianteRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.TipoDocumentoRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.request.RegistrarPagoRequest;
import cr.acusab.una.sigac.presentation.dto.response.EstadoMesPagoResponse;
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

    private final MensualidadEstudianteRepository mensualidadEstudianteRepository;
    private final EstudianteRepository estudianteRepository;
    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final BecaRepository becaRepository;
    private final MensualidadCalculoService mensualidadCalculoService;
    private final DocumentoEstudianteRepository documentoEstudianteRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;
    private final UsuarioRepository usuarioRepository;

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
            UsuarioRepository usuarioRepository
    ) {
        this.mensualidadEstudianteRepository = mensualidadEstudianteRepository;
        this.estudianteRepository = estudianteRepository;
        this.periodoAcademicoRepository = periodoAcademicoRepository;
        this.becaRepository = becaRepository;
        this.mensualidadCalculoService = mensualidadCalculoService;
        this.documentoEstudianteRepository = documentoEstudianteRepository;
        this.tipoDocumentoRepository = tipoDocumentoRepository;
        this.usuarioRepository = usuarioRepository;
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

        TiposDocumento tipoComprobante = tipoDocumentoRepository
                .findByCodigoAndActivoTrue(CODIGO_TIPO_DOCUMENTO_COMPROBANTE)
                .orElseThrow(() -> new BusinessException(
                        "No está configurado el tipo de documento " + CODIGO_TIPO_DOCUMENTO_COMPROBANTE));

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
        mensualidad.setFechaPago(request.getFechaPago());
        mensualidad.setIdDocumentoComprobante(documento);
        mensualidad.setPagada(true);
        mensualidad.setFechaConfirmacion(ahora);
        mensualidad.setIdUsuarioConfirma(usuarioRegistra);
        mensualidad.setObservaciones(normalizarNullable(request.getObservaciones()));

        mensualidad = mensualidadEstudianteRepository.save(mensualidad);

        return construirRespuesta(mensualidad, estudiante, periodo, becaActiva,
                "Pago registrado correctamente.");
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
        }

        response.setMontoEsperado(becado100 ? BigDecimal.ZERO.setScale(2) : montoEsperado);

        return response;
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
