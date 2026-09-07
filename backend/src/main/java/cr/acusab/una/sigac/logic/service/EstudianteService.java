package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.*;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.exception.DuplicateResourceException;
import cr.acusab.una.sigac.presentation.dto.EstudianteCreateRequest;
import cr.acusab.una.sigac.presentation.dto.EstudianteResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class EstudianteService {

    private static final long MAX_PDF_SIZE = 10L * 1024L * 1024L;
    private static final List<String> GENEROS = List.of("MASCULINO", "FEMENINO", "NO_BINARIO", "OTRO", "PREFIERO_NO_DECIR");

    private final EstudianteRepository estudianteRepository;
    private final EncargadoRepository encargadoRepository;
    private final EstudianteEncargadoRepository estudianteEncargadoRepository;
    private final InstrumentoRepository instrumentoRepository;
    private final EstudianteInstrumentoRepository estudianteInstrumentoRepository;
    private final DocumentoEstudianteRepository documentoEstudianteRepository;
    private final TipoIdentificacionRepository tipoIdentificacionRepository;
    private final CentroEducativoRepository centroEducativoRepository;

    @Value("${sigac.storage.documents:./storage/documents}")
    private String storageDirectory;

    public EstudianteService(
            EstudianteRepository estudianteRepository,
            EncargadoRepository encargadoRepository,
            EstudianteEncargadoRepository estudianteEncargadoRepository,
            InstrumentoRepository instrumentoRepository,
            EstudianteInstrumentoRepository estudianteInstrumentoRepository,
            DocumentoEstudianteRepository documentoEstudianteRepository,
            TipoIdentificacionRepository tipoIdentificacionRepository,
            CentroEducativoRepository centroEducativoRepository) {
        this.estudianteRepository = estudianteRepository;
        this.encargadoRepository = encargadoRepository;
        this.estudianteEncargadoRepository = estudianteEncargadoRepository;
        this.instrumentoRepository = instrumentoRepository;
        this.estudianteInstrumentoRepository = estudianteInstrumentoRepository;
        this.documentoEstudianteRepository = documentoEstudianteRepository;
        this.tipoIdentificacionRepository = tipoIdentificacionRepository;
        this.centroEducativoRepository = centroEducativoRepository;
    }

    @Transactional
    public EstudianteResponse crear(EstudianteCreateRequest request, MultipartFile politica) {
        String identificacion = normalizar(request.numeroIdentificacion());
        if (estudianteRepository.existsByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                request.idTipoIdentificacion(), identificacion)) {
            throw new DuplicateResourceException("Ya existe un estudiante con esa identificación");
        }

        validarGenero(request.genero());
        validarPdf(politica);

        LocalDate nacimiento = request.fechaNacimiento();
        int edad = Period.between(nacimiento, LocalDate.now()).getYears();
        boolean menor = edad < 18;

        if (menor) {
            validarEncargados(request.encargados());
            if (request.idCentroEducativo() == null) {
                throw new BusinessException("Para un menor en edad escolar debe indicar el centro educativo");
            }
        } else if (request.encargados() != null && !request.encargados().isEmpty()) {
            throw new BusinessException("Los encargados solo se registran durante el alta de un menor");
        }

        if (request.idCentroEducativo() != null) {
            centroEducativoRepository.findById(request.idCentroEducativo())
                    .orElseThrow(() -> new BusinessException("El centro educativo indicado no existe"));
        }

        Instrumento instrumento = instrumentoRepository.findById(request.idInstrumento())
                .orElseThrow(() -> new BusinessException("El instrumento indicado no existe"));

        TipoIdentificacion tipo = tipoIdentificacionRepository.findById(request.idTipoIdentificacion())
                .orElseThrow(() -> new BusinessException("El tipo de identificación indicado no existe"));

        Estudiante estudiante = new Estudiante();
        estudiante.setIdTipoIdentificacion(tipo);
        estudiante.setNumeroIdentificacion(identificacion);
        estudiante.setNombres(normalizar(request.nombres()));
        estudiante.setPrimerApellido(normalizar(request.primerApellido()));
        estudiante.setSegundoApellido(normalizarNullable(request.segundoApellido()));
        estudiante.setFechaNacimiento(nacimiento);
        estudiante.setGenero(normalizar(request.genero()).toUpperCase(Locale.ROOT));
        estudiante.setTelefono(normalizarNullable(request.telefono()));
        estudiante.setCorreo(normalizarNullable(request.correo()));
        estudiante.setDireccion(normalizarNullable(request.direccion()));
        estudiante.setNivelEscolar(normalizarNullable(request.nivelEscolar()));
        estudiante.setCondicionesRelevantes(normalizarNullable(request.condicionesRelevantes()));
        estudiante.setFechaIngreso(LocalDate.now());
        estudiante.setActivo(true);
        if (request.idCentroEducativo() != null) {
            estudiante.setIdCentroEducativo(centroEducativoRepository.getReferenceById(request.idCentroEducativo()));
        }
        estudiante = estudianteRepository.save(estudiante);

        EstudianteInstrumento estudianteInstrumento = new EstudianteInstrumento();
        estudianteInstrumento.setEstudiante(estudiante);
        estudianteInstrumento.setInstrumento(instrumento);
        estudianteInstrumento.setFechaInicio(LocalDate.now());
        estudianteInstrumento.setEsPrincipal(true);
        estudianteInstrumentoRepository.save(estudianteInstrumento);

        if (menor) {
            for (EstudianteCreateRequest.EncargadoRequest data : request.encargados()) {
                Encargado encargado = obtenerOCrearEncargado(data);
                EstudianteEncargado relacion = new EstudianteEncargado();
                relacion.setEstudiante(estudiante);
                relacion.setEncargado(encargado);
                relacion.setParentesco(data.parentesco().toUpperCase(Locale.ROOT));
                relacion.setEsResponsablePrincipal(data.responsablePrincipal());
                relacion.setEsContactoEmergencia(data.contactoEmergencia());
                estudianteEncargadoRepository.save(relacion);
            }
        }

        guardarPolitica(estudiante, politica);

        return new EstudianteResponse(
                estudiante.getId(), identificacion, estudiante.getNombres(), estudiante.getPrimerApellido(),
                estudiante.getSegundoApellido(), estudiante.getFechaNacimiento(), edad, estudiante.getGenero(),
                estudiante.getTelefono(), estudiante.getCorreo(), estudiante.getNivelEscolar(),
                estudiante.getIdCentroEducativo() == null ? null : estudiante.getIdCentroEducativo().getId(),
                instrumento.getId(), menor);
    }

    private Encargado obtenerOCrearEncargado(EstudianteCreateRequest.EncargadoRequest data) {
        String identificacion = normalizarNullable(data.numeroIdentificacion());
        if (data.idTipoIdentificacion() != null && identificacion != null) {
            var existente = encargadoRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                    data.idTipoIdentificacion(), identificacion);
            if (existente.isPresent()) return existente.get();
        }

        Encargado encargado = new Encargado();
        if (data.idTipoIdentificacion() != null) {
            encargado.setIdTipoIdentificacion(tipoIdentificacionRepository.findById(data.idTipoIdentificacion())
                    .orElseThrow(() -> new BusinessException("El tipo de identificación del encargado no existe")));
        }
        encargado.setNumeroIdentificacion(identificacion);
        encargado.setNombres(normalizar(data.nombres()));
        encargado.setPrimerApellido(normalizar(data.primerApellido()));
        encargado.setSegundoApellido(normalizarNullable(data.segundoApellido()));
        encargado.setTelefonoPrincipal(normalizar(data.telefonoPrincipal()));
        encargado.setTelefonoSecundario(normalizarNullable(data.telefonoSecundario()));
        encargado.setCorreo(normalizarNullable(data.correo()));
        encargado.setOcupacion(normalizarNullable(data.ocupacion()));
        encargado.setActivo(true);
        return encargadoRepository.save(encargado);
    }

    private void validarEncargados(List<EstudianteCreateRequest.EncargadoRequest> encargados) {
        if (encargados == null || encargados.isEmpty() || encargados.size() > 2) {
            throw new BusinessException("Un menor debe tener uno o dos encargados");
        }
        long principales = encargados.stream().filter(EstudianteCreateRequest.EncargadoRequest::responsablePrincipal).count();
        long emergencias = encargados.stream().filter(EstudianteCreateRequest.EncargadoRequest::contactoEmergencia).count();
        if (principales != 1) {
            throw new BusinessException("Debe existir exactamente un responsable principal");
        }
        if (emergencias > 2) {
            throw new BusinessException("No puede haber más de dos contactos de emergencia");
        }
        for (var encargado : encargados) {
            String parentesco = normalizar(encargado.parentesco()).toUpperCase(Locale.ROOT);
            if (!List.of("MADRE", "PADRE", "TUTOR", "FAMILIAR", "OTRO").contains(parentesco)) {
                throw new BusinessException("El parentesco no es válido");
            }
        }
    }

    private void validarGenero(String genero) {
        if (!GENEROS.contains(normalizar(genero).toUpperCase(Locale.ROOT))) {
            throw new BusinessException("El género seleccionado no es válido");
        }
    }

    private void validarPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("La póliza en PDF es obligatoria");
        if (file.getSize() > MAX_PDF_SIZE) throw new BusinessException("La póliza no puede superar 10 MB");
        String contentType = Objects.toString(file.getContentType(), "").toLowerCase(Locale.ROOT);
        String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".pdf") || !contentType.equals("application/pdf")) {
            throw new BusinessException("La póliza debe ser un archivo PDF");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(5);
            if (header.length < 5 || !new String(header).equals("%PDF-")) {
                throw new BusinessException("El archivo no es un PDF válido");
            }
        } catch (IOException e) {
            throw new BusinessException("No fue posible leer la póliza");
        }
    }

    private void guardarPolitica(Estudiante estudiante, MultipartFile file) {
        Path studentDir = Paths.get(storageDirectory).toAbsolutePath().normalize().resolve(String.valueOf(estudiante.getId()));
        String storedName = UUID.randomUUID() + ".pdf";
        Path destination = studentDir.resolve(storedName).normalize();
        if (!destination.startsWith(studentDir)) throw new BusinessException("Ruta de almacenamiento inválida");
        try {
            Files.createDirectories(studentDir);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            DocumentoEstudiante documento = new DocumentoEstudiante();
            documento.setEstudiante(estudiante);
            documento.setNombreOriginal(file.getOriginalFilename());
            documento.setNombreAlmacenado(storedName);
            documento.setUbicacionArchivo(destination.toString());
            documento.setTipoMime("application/pdf");
            documento.setTamanoBytes(file.getSize());
            documento.setSha256(sha256(destination));
            documento.setTipoDocumento("POLIZA");
            documentoEstudianteRepository.save(documento);
        } catch (IOException e) {
            throw new BusinessException("No fue posible almacenar la póliza");
        }
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

    private String normalizar(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " "); }
    private String normalizarNullable(String value) {
        String result = normalizar(value);
        return result.isBlank() ? null : result;
    }
}
