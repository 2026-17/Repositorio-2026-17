package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.DocumentoEstudiante;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.TipoDocumento;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.DocumentoEstudianteRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.TipoDocumentoRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.presentation.dto.response.DocumentoEstudianteResponse;
import cr.acusab.una.sigac.presentation.dto.response.TipoDocumentoResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentoEstudianteService {

    private static final String CONTENT_TYPE_PDF = "application/pdf";

    private final DocumentoEstudianteRepository documentoEstudianteRepository;
    private final EstudianteRepository estudianteRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final Path storageBasePath;

    public DocumentoEstudianteService(
            DocumentoEstudianteRepository documentoEstudianteRepository,
            EstudianteRepository estudianteRepository,
            TipoDocumentoRepository tipoDocumentoRepository,
            UsuarioRepository usuarioRepository,
            @Value("${app.storage.documentos-estudiantes}") String storageBasePath
    ) {
        this.documentoEstudianteRepository = documentoEstudianteRepository;
        this.estudianteRepository = estudianteRepository;
        this.tipoDocumentoRepository = tipoDocumentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.storageBasePath = Path.of(storageBasePath);
    }

    @Transactional
    public DocumentoEstudianteResponse adjuntar(
            Long idEstudiante,
            Integer idTipoDocumento,
            MultipartFile archivo,
            LocalDate fechaEmision,
            LocalDate fechaVencimiento
    ) {
        Estudiante estudiante = estudianteRepository.findById(idEstudiante)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado"));

        TipoDocumento tipoDocumento = tipoDocumentoRepository.findById(idTipoDocumento)
                .filter(TipoDocumento::getActivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de documento no existe o esta inactivo"));

        byte[] contenido = validarPdf(archivo);
        String sha256 = calcularSha256(contenido);

        documentoEstudianteRepository
                .findByIdEstudiante_IdAndIdTipoDocumento_IdAndEsActualTrue(idEstudiante, idTipoDocumento)
                .ifPresent(vigente -> {
                    vigente.setEsActual(false);
                    documentoEstudianteRepository.save(vigente);
                });

        String nombreAlmacenado = UUID.randomUUID() + ".pdf";
        String ubicacionArchivo = guardarEnDisco(idEstudiante, nombreAlmacenado, contenido);

        DocumentoEstudiante documento = new DocumentoEstudiante();
        documento.setIdEstudiante(estudiante);
        documento.setIdTipoDocumento(tipoDocumento);
        documento.setNombreOriginal(archivo.getOriginalFilename());
        documento.setNombreAlmacenado(nombreAlmacenado);
        documento.setUbicacionArchivo(ubicacionArchivo);
        documento.setTipoMime(CONTENT_TYPE_PDF);
        documento.setTamanoBytes((long) contenido.length);
        documento.setSha256(sha256);
        documento.setFechaEmision(fechaEmision);
        documento.setFechaVencimiento(fechaVencimiento);
        documento.setEsActual(true);
        documento.setIdUsuarioCarga(usuarioActual());
        documento.setCargadoEn(Instant.now());

        DocumentoEstudiante guardado = documentoEstudianteRepository.save(documento);
        return toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<DocumentoEstudianteResponse> listarPorEstudiante(Long idEstudiante) {
        if (!estudianteRepository.existsById(idEstudiante)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado");
        }
        return documentoEstudianteRepository.findByIdEstudiante_IdAndEsActualTrue(idEstudiante)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // El sistema debe permitir unicamente archivos PDF y validar que no esten danados.
    private byte[] validarPdf(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe adjuntar un archivo");
        }

        if (!CONTENT_TYPE_PDF.equalsIgnoreCase(archivo.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El sistema solo permite archivos en formato PDF");
        }

        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try (PDDocument ignored = Loader.loadPDF(contenido)) {
            // Si el archivo se puede cargar, la estructura del PDF es valida.
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo PDF esta danado o no es valido");
        }

        return contenido;
    }

    private String calcularSha256(byte[] contenido) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String guardarEnDisco(Long idEstudiante, String nombreAlmacenado, byte[] contenido) {
        try {
            Path carpetaEstudiante = storageBasePath.resolve(String.valueOf(idEstudiante));
            Files.createDirectories(carpetaEstudiante);
            Path destino = carpetaEstudiante.resolve(nombreAlmacenado);
            Files.write(destino, contenido);
            return destino.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Resuelve el usuario autenticado (username = cedula, ver UsuarioDetailsService)
    // para completar id_usuario_carga.
    private Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return usuarioRepository.findByCedula(auth.getName()).orElse(null);
    }

    private DocumentoEstudianteResponse toResponse(DocumentoEstudiante d) {
        TipoDocumento tipo = d.getIdTipoDocumento();
        return new DocumentoEstudianteResponse(
                d.getId(),
                d.getIdEstudiante().getId(),
                new TipoDocumentoResponse(tipo.getId(), tipo.getCodigo(), tipo.getNombre(), tipo.getDescripcion()),
                d.getNombreOriginal(),
                d.getTipoMime(),
                d.getTamanoBytes(),
                d.getFechaEmision(),
                d.getFechaVencimiento(),
                d.getEsActual(),
                d.getCargadoEn()
        );
    }
}
