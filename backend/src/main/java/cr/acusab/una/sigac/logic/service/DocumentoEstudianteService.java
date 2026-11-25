package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.DocumentosEstudiante;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.TiposDocumento;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.DocumentoEstudianteRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.TipoDocumentoRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.util.ValidadorArchivo;
import cr.acusab.una.sigac.presentation.dto.response.DocumentoEstudianteResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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

@Service
public class DocumentoEstudianteService {

    private static final long MAX_PDF_SIZE = 50L * 1024L * 1024L;

    // La póliza también acepta imágenes; los demás documentos, solo PDF.
    private static final String CODIGO_POLIZA = "POLIZA";
    private static final String MENSAJE_POLIZA_TIPO = "La póliza debe ser un PDF o una imagen (JPG, PNG o WEBP).";

    private final DocumentoEstudianteRepository documentoEstudianteRepository;
    private final EstudianteRepository estudianteRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${sigac.storage.documents:./storage/documents}")
    private String storageDirectory;

    public DocumentoEstudianteService(
            DocumentoEstudianteRepository documentoEstudianteRepository,
            EstudianteRepository estudianteRepository,
            TipoDocumentoRepository tipoDocumentoRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.documentoEstudianteRepository = documentoEstudianteRepository;
        this.estudianteRepository = estudianteRepository;
        this.tipoDocumentoRepository = tipoDocumentoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * HU-022:
     * Adjunta un documento PDF al expediente del estudiante.
     *
     * Si ya existe un documento vigente del mismo tipo, el anterior
     * se conserva como historial pero deja de ser el documento actual.
     */
    @Transactional
    public DocumentoEstudianteResponse adjuntarOReemplazar(
            Long idEstudiante,
            Integer idTipoDocumento,
            MultipartFile archivo,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento
    ) {
        Estudiante estudiante = estudianteRepository
                .findByIdAndActivoTrue(idEstudiante)
                .orElseThrow(() ->
                        new BusinessException("El estudiante indicado no existe o está inactivo."));

        TiposDocumento tipoDocumento = tipoDocumentoRepository
                .findById(idTipoDocumento)
                .filter(tipo -> Boolean.TRUE.equals(tipo.getActivo()))
                .orElseThrow(() ->
                        new BusinessException("El tipo de documento indicado no existe o está inactivo."));

        validarFechas(fechaEmision, fechaVencimiento);

        // La póliza puede ser PDF o imagen; los demás documentos, solo PDF.
        boolean esPoliza = esPoliza(tipoDocumento);
        String tipoMime = esPoliza ? validarPoliza(archivo) : validarPdfYDevolverTipo(archivo);

        Usuario usuarioCarga = obtenerUsuarioAutenticado();

        Path studentDir = Paths.get(storageDirectory)
                .toAbsolutePath()
                .normalize()
                .resolve(String.valueOf(estudiante.getId()));

        // Nombre aleatorio con la extensión del tipo real (.pdf, .jpg, .png, .webp).
        String storedName = ValidadorArchivo.nombreAlmacenado(tipoMime);

        Path destination = studentDir
                .resolve(storedName)
                .normalize();

        if (!destination.startsWith(studentDir)) {
            throw new BusinessException("Ruta de almacenamiento inválida.");
        }

        boolean archivoGuardado = false;

        try {
            Files.createDirectories(studentDir);

            Files.copy(
                    archivo.getInputStream(),
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );

            archivoGuardado = true;
            borrarArchivoSiLaTransaccionFalla(destination);

            String hash = sha256(destination);

            /*
             * Si existe un documento actual del mismo tipo, deja de ser
             * vigente, pero no se elimina. De esta forma se conserva
             * el historial del expediente.
             */
            documentoEstudianteRepository
                    .findFirstByIdEstudiante_IdAndIdTipoDocumento_IdAndEsActualTrueOrderByCargadoEnDesc(
                            estudiante.getId(),
                            tipoDocumento.getId()
                    )
                    .ifPresent(documentoAnterior -> {
                        documentoAnterior.setEsActual(false);
                        documentoEstudianteRepository.save(documentoAnterior);
                    });

            DocumentosEstudiante documento = new DocumentosEstudiante();

            documento.setEstudiante(estudiante);
            documento.setIdTipoDocumento(tipoDocumento);
            documento.setNombreOriginal(nombreOriginalSeguro(archivo));
            documento.setNombreAlmacenado(storedName);
            documento.setUbicacionArchivo(destination.toString());
            documento.setTipoMime(tipoMime);
            documento.setTamanoBytes(archivo.getSize());
            documento.setSha256(hash);
            documento.setFechaEmision(fechaEmision);
            documento.setFechaVencimiento(fechaVencimiento);
            documento.setEsActual(true);
            documento.setIdUsuarioCarga(usuarioCarga);
            documento.setCargadoEn(Instant.now());

            DocumentosEstudiante guardado =
                    documentoEstudianteRepository.save(documento);

            return convertirResponse(guardado);

        } catch (IOException e) {

            if (archivoGuardado) {
                eliminarArchivoSilenciosamente(destination);
            }

            throw new BusinessException(
                    "No fue posible almacenar el documento del estudiante."
            );

        } catch (RuntimeException e) {

            /*
             * Si la base de datos falla después de haber escrito el archivo,
             * se elimina el archivo recién creado para evitar archivos
             * huérfanos. La transacción revierte los cambios de base de datos.
             */
            if (archivoGuardado) {
                eliminarArchivoSilenciosamente(destination);
            }

            throw e;
        }
    }

    /**
     * Revisa el PDF y sus fechas sin guardar nada. La edición completa lo usa
     * para reportar todos los errores antes de empezar a escribir.
     */
    public void validarAdjunto(
            MultipartFile archivo,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento
    ) {
        validarFechas(fechaEmision, fechaVencimiento);
        validarPdf(archivo);
    }

    /**
     * Igual que validarAdjunto, pero para la póliza: acepta PDF o imagen
     * (JPG, PNG o WEBP). La usa la edición completa del estudiante.
     */
    public void validarAdjuntoPoliza(
            MultipartFile archivo,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento
    ) {
        validarFechas(fechaEmision, fechaVencimiento);
        validarPoliza(archivo);
    }

    private static boolean esPoliza(TiposDocumento tipoDocumento) {
        return tipoDocumento != null && CODIGO_POLIZA.equalsIgnoreCase(tipoDocumento.getCodigo());
    }

    private String validarPdfYDevolverTipo(MultipartFile archivo) {
        validarPdf(archivo);
        return ValidadorArchivo.PDF;
    }

    /*
     * Póliza: PDF o imagen (JPG, PNG o WEBP), hasta 50 MB.
     * - PDF: la misma validación de siempre (PDFBox debe poder abrirlo).
     * - Imagen: sus primeros bytes deben ser los de ese formato.
     * Devuelve el tipo MIME real con que se guarda.
     */
    private String validarPoliza(MultipartFile archivo) {

        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("Debe adjuntar la póliza.");
        }

        if (archivo.getSize() > MAX_PDF_SIZE) {
            throw new BusinessException("La póliza no puede superar 50 MB.");
        }

        String tipoMime = ValidadorArchivo.tipoMime(archivo);

        if (!ValidadorArchivo.esPdfOImagen(tipoMime)) {
            throw new BusinessException(MENSAJE_POLIZA_TIPO);
        }

        if (ValidadorArchivo.PDF.equals(tipoMime)) {
            validarPdf(archivo);
            return tipoMime;
        }

        if (Objects.toString(archivo.getOriginalFilename(), "").trim().length() > 255) {
            throw new BusinessException("El nombre del archivo no puede superar 255 caracteres.");
        }

        boolean coincide;
        try {
            coincide = ValidadorArchivo.contenidoCoincide(archivo, tipoMime);
        } catch (IOException e) {
            throw new BusinessException("No fue posible leer la póliza.");
        }

        if (!coincide) {
            throw new BusinessException(MENSAJE_POLIZA_TIPO);
        }

        return tipoMime;
    }

    /*
     * El archivo no forma parte de la transacción de la base de datos: si
     * esta se revierte después de copiarlo (incluso al hacer commit), hay
     * que borrarlo a mano para no dejar un PDF huérfano.
     */
    private void borrarArchivoSiLaTransaccionFalla(Path archivo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    eliminarArchivoSilenciosamente(archivo);
                }
            }
        });
    }

    /**
     * HU-022:
     * Lista los documentos actualmente vigentes del expediente.
     */
    @Transactional(readOnly = true)
    public List<DocumentoEstudianteResponse> listarActuales(Long idEstudiante) {

        estudianteRepository
                .findByIdAndActivoTrue(idEstudiante)
                .orElseThrow(() ->
                        new BusinessException("El estudiante indicado no existe o está inactivo."));

        return documentoEstudianteRepository
                .findByIdEstudiante_IdAndEsActualTrueOrderByCargadoEnDesc(idEstudiante)
                .stream()
                .map(this::convertirResponse)
                .toList();
    }

    /**
     * Valida que el archivo sea realmente un PDF utilizable.
     *
     * Se verifica:
     * - que exista;
     * - que no esté vacío;
     * - tamaño máximo;
     * - extensión;
     * - tipo MIME;
     * - que PDFBox pueda abrirlo correctamente.
     */
    private void validarPdf(MultipartFile archivo) {

        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("Debe adjuntar un archivo PDF.");
        }

        if (archivo.getSize() > MAX_PDF_SIZE) {
            throw new BusinessException(
                    "El documento PDF no puede superar 50 MB."
            );
        }

        String nombre = Objects
                .toString(archivo.getOriginalFilename(), "")
                .trim();

        String tipoMime = Objects
                .toString(archivo.getContentType(), "")
                .toLowerCase(Locale.ROOT);

        if (!nombre.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new BusinessException(
                    "El archivo adjunto debe tener extensión PDF."
            );
        }

        if (!"application/pdf".equals(tipoMime)) {
            throw new BusinessException(
                    "El archivo adjunto debe ser de tipo PDF."
            );
        }

        if (nombre.length() > 255) {
            throw new BusinessException(
                    "El nombre del archivo no puede superar 255 caracteres."
            );
        }

        /*
         * PDFBox intenta interpretar la estructura completa del archivo.
         * Un archivo que solamente tenga extensión .pdf o encabezado %PDF-
         * pero cuya estructura esté dañada será rechazado aquí.
         */
        try {
            byte[] contenido = archivo.getBytes();

            try (PDDocument ignored = Loader.loadPDF(contenido)) {
                // Si PDFBox logra abrirlo, el archivo es procesable.
            }

        } catch (IOException | RuntimeException e) {
            throw new BusinessException(
                    "El archivo PDF está dañado o no es un PDF válido."
            );
        }
    }

    private void validarFechas(
            LocalDate fechaEmision,
            LocalDate fechaVencimiento
    ) {
        if (fechaEmision != null
                && fechaVencimiento != null
                && fechaVencimiento.isBefore(fechaEmision)) {

            throw new BusinessException(
                    "La fecha de vencimiento no puede ser anterior a la fecha de emisión."
            );
        }
    }

    private String nombreOriginalSeguro(MultipartFile archivo) {

        String nombre = Objects
                .toString(archivo.getOriginalFilename(), "documento.pdf")
                .trim();

        /*
         * Algunos navegadores/clientes pueden enviar una ruta junto
         * al nombre. Solo conservamos el nombre final como metadata.
         */
        nombre = nombre.replace("\\", "/");

        int ultimaBarra = nombre.lastIndexOf('/');

        if (ultimaBarra >= 0) {
            nombre = nombre.substring(ultimaBarra + 1);
        }

        if (nombre.isBlank()) {
            return "documento.pdf";
        }

        return nombre;
    }

    private String sha256(Path path) throws IOException {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            try (InputStream input = Files.newInputStream(path)) {

                byte[] buffer = new byte[8192];
                int read;

                while ((read = input.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }

            StringBuilder hex = new StringBuilder();

            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();

        } catch (Exception e) {
            throw new IOException(
                    "No fue posible calcular SHA-256.",
                    e
            );
        }
    }

    /**
     * Reutiliza el mismo mecanismo de autenticación utilizado
     * por PagoService.
     */
    private Usuario obtenerUsuarioAutenticado() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {

            throw new IllegalStateException(
                    "No se pudo identificar al usuario autenticado."
            );
        }

        String cedula = authentication.getName();

        return usuarioRepository
                .findByCedula(cedula)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "El usuario autenticado no existe en el sistema."
                        ));
    }

    private DocumentoEstudianteResponse convertirResponse(
            DocumentosEstudiante documento
    ) {
        return new DocumentoEstudianteResponse(
                documento.getId(),
                documento.getIdEstudiante().getId(),
                documento.getIdTipoDocumento().getId(),
                documento.getIdTipoDocumento().getCodigo(),
                documento.getIdTipoDocumento().getNombre(),
                documento.getNombreOriginal(),
                documento.getTipoMime(),
                documento.getTamanoBytes(),
                documento.getFechaEmision(),
                documento.getFechaVencimiento(),
                documento.getEsActual(),
                documento.getCargadoEn()
        );
    }

    private void eliminarArchivoSilenciosamente(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Limpieza best-effort.
        }
    }
}