package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.*;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.exception.DuplicateResourceException;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteCreateRequest;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteResponse;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteUpdateRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class EstudianteService {
    private static final long MAX_PDF_SIZE = 50L * 1024L * 1024L;
    private static final List<String> GENEROS = List.of("MASCULINO", "FEMENINO", "OTRO");
    private static final List<String> PARENTESCOS = List.of("MADRE", "PADRE", "TUTOR", "FAMILIAR", "OTRO");

    private final EstudianteRepository estudianteRepository;
    private final EncargadoRepository encargadoRepository;
    private final EstudianteEncargadoRepository estudianteEncargadoRepository;
    private final InstrumentoRepository instrumentoRepository;
    private final EstudianteInstrumentoRepository estudianteInstrumentoRepository;
    private final DocumentoEstudianteRepository documentoEstudianteRepository;
    private final TipoIdentificacionRepository tipoIdentificacionRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;
    private final CentroEducativoRepository centroEducativoRepository;

    @Value("${sigac.storage.documents:./storage/documents}")
    private String storageDirectory;

    public EstudianteService(EstudianteRepository estudianteRepository,
                             EncargadoRepository encargadoRepository,
                             EstudianteEncargadoRepository estudianteEncargadoRepository,
                             InstrumentoRepository instrumentoRepository,
                             EstudianteInstrumentoRepository estudianteInstrumentoRepository,
                             DocumentoEstudianteRepository documentoEstudianteRepository,
                             TipoIdentificacionRepository tipoIdentificacionRepository,
                             TipoDocumentoRepository tipoDocumentoRepository,
                             CentroEducativoRepository centroEducativoRepository) {
        this.estudianteRepository = estudianteRepository;
        this.encargadoRepository = encargadoRepository;
        this.estudianteEncargadoRepository = estudianteEncargadoRepository;
        this.instrumentoRepository = instrumentoRepository;
        this.estudianteInstrumentoRepository = estudianteInstrumentoRepository;
        this.documentoEstudianteRepository = documentoEstudianteRepository;
        this.tipoIdentificacionRepository = tipoIdentificacionRepository;
        this.tipoDocumentoRepository = tipoDocumentoRepository;
        this.centroEducativoRepository = centroEducativoRepository;
    }

    @Transactional
    public EstudianteResponse crear(EstudianteCreateRequest request, MultipartFile politica) {
        String identificacion = normalizar(request.numeroIdentificacion());
        if (estudianteRepository.existsByIdTipoIdentificacion_IdAndNumeroIdentificacion(request.idTipoIdentificacion(), identificacion)) {
            throw new DuplicateResourceException("Ya existe un estudiante con esa identificación");
        }
        validarGenero(request.genero());
        validarPdf(politica);
        int edad = calcularEdad(request.fechaNacimiento());
        boolean menor = edad < 18;
        if (menor) {
            validarEncargados(request.encargados());
        } else if (request.encargados() != null && !request.encargados().isEmpty()) {
            throw new BusinessException("Los encargados solo se registran para menores de edad");
        }

        CentrosEducativo centro = obtenerCentro(request.idCentroEducativo(), edad);
        Instrumento instrumento = instrumentoRepository.findByIdAndActivoTrue(request.idInstrumento())
                .orElseThrow(() -> new BusinessException("El instrumento indicado no existe o está inactivo"));
        TiposIdentificacion tipo = tipoIdentificacionRepository.findByIdAndActivoTrue(request.idTipoIdentificacion())
                .orElseThrow(() -> new BusinessException("El tipo de identificación indicado no existe o está inactivo"));
        TiposDocumento tipoPoliza = tipoDocumentoRepository.findByCodigoAndActivoTrue("POLIZA")
                .orElseThrow(() -> new BusinessException("No está configurado el tipo de documento POLIZA activo"));

        Estudiante estudiante = new Estudiante();
        aplicarDatos(estudiante, tipo, identificacion, request.nombres(), request.primerApellido(), request.segundoApellido(), request.fechaNacimiento(),
                request.genero(), request.direccion(), request.telefono(), request.correo(), request.nivelEscolar(), centro, request.condicionesRelevantes());
        estudiante.setFechaIngreso(LocalDate.now());
        estudiante.setActivo(true);
        Instant ahora = Instant.now();
        estudiante.setCreadoEn(ahora);
        estudiante.setActualizadoEn(ahora);
        estudiante = estudianteRepository.save(estudiante);

        crearRelacionInstrumento(estudiante, instrumento);

        if (menor) {
            for (var data : request.encargados()) {
                Encargado encargado = obtenerOCrearEncargado(data);
                EstudiantesEncargado relacion = new EstudiantesEncargado();
                relacion.setId(new EstudiantesEncargadoId());
                relacion.setEstudiante(estudiante);
                relacion.setEncargado(encargado);
                relacion.getId().setIdEstudiante(estudiante.getId());
                relacion.getId().setIdEncargado(encargado.getId());
                relacion.setParentesco(normalizar(data.parentesco()).toUpperCase(Locale.ROOT));
                relacion.setEsResponsablePrincipal(data.responsablePrincipal());
                relacion.setEsContactoEmergencia(data.contactoEmergencia());
                estudianteEncargadoRepository.save(relacion);
            }
        }

        guardarPolitica(estudiante, politica, tipoPoliza);
        return respuesta(estudiante, instrumento.getId(), edad);
    }

    @Transactional(readOnly = true)
    public EstudianteResponse obtener(Long id) {
        Estudiante estudiante = estudianteRepository.findById(id)
                .orElseThrow(() -> new BusinessException("El estudiante indicado no existe"));
        Integer instrumentoId = estudianteInstrumentoRepository.findAll().stream()
                .filter(x -> x.getEstudiante() != null && Objects.equals(x.getEstudiante().getId(), id)
                        && Boolean.TRUE.equals(x.getEsPrincipal()))
                .map(x -> x.getInstrumento().getId()).findFirst().orElse(null);
        return respuesta(estudiante, instrumentoId, calcularEdad(estudiante.getFechaNacimiento()));
    }

    @Transactional
    public EstudianteResponse actualizar(Long id, EstudianteUpdateRequest request) {
        Estudiante estudiante = estudianteRepository.findById(id)
                .orElseThrow(() -> new BusinessException("El estudiante indicado no existe"));
        String identificacion = normalizar(request.numeroIdentificacion());
        var existente = estudianteRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(request.idTipoIdentificacion(), identificacion);
        if (existente.isPresent() && !Objects.equals(existente.get().getId(), id)) {
            throw new DuplicateResourceException("Ya existe un estudiante con esa identificación");
        }
        validarGenero(request.genero());
        int edad = calcularEdad(request.fechaNacimiento());
        CentrosEducativo centro = obtenerCentro(request.idCentroEducativo(), edad);
        Instrumento instrumento = instrumentoRepository.findByIdAndActivoTrue(request.idInstrumento())
                .orElseThrow(() -> new BusinessException("El instrumento indicado no existe o está inactivo"));
        TiposIdentificacion tipo = tipoIdentificacionRepository.findByIdAndActivoTrue(request.idTipoIdentificacion())
                .orElseThrow(() -> new BusinessException("El tipo de identificación indicado no existe o está inactivo"));

        aplicarDatos(estudiante, tipo, identificacion, request.nombres(), request.primerApellido(), request.segundoApellido(), request.fechaNacimiento(),
                request.genero(), request.direccion(), request.telefono(), request.correo(), request.nivelEscolar(), centro, request.condicionesRelevantes());
        estudiante.setActualizadoEn(Instant.now());
        estudianteRepository.save(estudiante);

        var principales = estudianteInstrumentoRepository.findAll().stream()
                .filter(x -> x.getEstudiante() != null && Objects.equals(x.getEstudiante().getId(), id)
                        && Boolean.TRUE.equals(x.getEsPrincipal())).toList();
        if (principales.isEmpty()) crearRelacionInstrumento(estudiante, instrumento);
        else { principales.get(0).setInstrumento(instrumento); estudianteInstrumentoRepository.save(principales.get(0)); }
        return respuesta(estudiante, instrumento.getId(), edad);
    }

    private void aplicarDatos(Estudiante e, TiposIdentificacion tipo, String identificacion, String nombres,
                              String primerApellido, String segundoApellido, LocalDate nacimiento, String genero,
                              String direccion, String telefono, String correo, String nivel, CentrosEducativo centro,
                              String condiciones) {
        e.setIdTipoIdentificacion(tipo);
        e.setNumeroIdentificacion(identificacion);
        e.setNombres(normalizar(nombres));
        e.setPrimerApellido(normalizar(primerApellido));
        e.setSegundoApellido(normalizarNullable(segundoApellido));
        e.setFechaNacimiento(nacimiento);
        e.setGenero(normalizar(genero).toUpperCase(Locale.ROOT));
        e.setDireccion(normalizar(direccion));
        e.setTelefono(normalizarNullable(telefono));
        e.setCorreo(normalizarNullable(correo));
        e.setNivelEscolar(normalizar(nivel));
        e.setIdCentroEducativo(centro);
        e.setCondicionesRelevantes(normalizarNullable(condiciones));
    }

    private void crearRelacionInstrumento(Estudiante estudiante, Instrumento instrumento) {
        EstudiantesInstrumento relacion = new EstudiantesInstrumento();
        relacion.setEstudiante(estudiante);
        relacion.setInstrumento(instrumento);
        relacion.setFechaInicio(LocalDate.now());
        relacion.setEsPrincipal(true);
        estudianteInstrumentoRepository.save(relacion);
    }

    private EstudianteResponse respuesta(Estudiante e, Integer instrumentoId, int edad) {
        return new EstudianteResponse(e.getId(), e.getNumeroIdentificacion(), e.getNombres(), e.getPrimerApellido(),
                e.getSegundoApellido(), e.getFechaNacimiento(), edad, e.getGenero(), e.getDireccion(), e.getTelefono(),
                e.getCorreo(), e.getNivelEscolar(), e.getIdCentroEducativo() == null ? null : e.getIdCentroEducativo().getId(),
                instrumentoId, edad < 18);
    }

    private int calcularEdad(LocalDate nacimiento) {
        if (nacimiento == null) throw new BusinessException("La fecha de nacimiento es obligatoria");
        return Period.between(nacimiento, LocalDate.now()).getYears();
    }

    private boolean requiereCentroEducativo(int edad) { return edad >= 6 && edad < 18; }

    private CentrosEducativo obtenerCentro(Long id, int edad) {
        if (requiereCentroEducativo(edad) && id == null) throw new BusinessException("Debe indicar el centro educativo para esta edad");
        if (!requiereCentroEducativo(edad) && id != null) throw new BusinessException("El centro educativo no corresponde para esta edad");
        if (id == null) return null;
        return centroEducativoRepository.findById(id).filter(c -> Boolean.TRUE.equals(c.getActivo()))
                .orElseThrow(() -> new BusinessException("El centro educativo indicado no existe o está inactivo"));
    }

    private Encargado obtenerOCrearEncargado(EstudianteCreateRequest.EncargadoRequest data) {
        String identificacion = normalizarNullable(data.numeroIdentificacion());
        if (data.idTipoIdentificacion() != null && identificacion != null) {
            var existente = encargadoRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(data.idTipoIdentificacion(), identificacion);
            if (existente.isPresent()) return existente.get();
        }
        Encargado encargado = new Encargado();
        if (data.idTipoIdentificacion() != null) {
            encargado.setIdTipoIdentificacion(tipoIdentificacionRepository.findByIdAndActivoTrue(data.idTipoIdentificacion())
                    .orElseThrow(() -> new BusinessException("El tipo de identificación del encargado no existe o está inactivo")));
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
        if (encargados == null || encargados.isEmpty() || encargados.size() > 2) throw new BusinessException("Un menor debe tener uno o dos encargados");
        long principales = encargados.stream().filter(EstudianteCreateRequest.EncargadoRequest::responsablePrincipal).count();
        long emergencias = encargados.stream().filter(EstudianteCreateRequest.EncargadoRequest::contactoEmergencia).count();
        if (principales != 1) throw new BusinessException("Debe existir exactamente un responsable principal");
        if (emergencias > 2) throw new BusinessException("No puede haber más de dos contactos de emergencia");
        for (var encargado : encargados) {
            if (!PARENTESCOS.contains(normalizar(encargado.parentesco()).toUpperCase(Locale.ROOT))) throw new BusinessException("El parentesco no es válido");
        }
    }

    private void validarGenero(String genero) {
        if (!GENEROS.contains(normalizar(genero).toUpperCase(Locale.ROOT))) throw new BusinessException("El género seleccionado no es válido");
    }

    private void validarPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("La póliza en PDF es obligatoria");
        if (file.getSize() > MAX_PDF_SIZE) throw new BusinessException("La póliza no puede superar 50 MB");
        String type = Objects.toString(file.getContentType(), "").toLowerCase(Locale.ROOT);
        String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".pdf") || !type.equals("application/pdf")) throw new BusinessException("La póliza debe ser un archivo PDF");
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(5);
            if (header.length < 5 || !new String(header).equals("%PDF-")) throw new BusinessException("El archivo no es un PDF válido");
        } catch (IOException e) { throw new BusinessException("No fue posible leer la póliza"); }
    }

    private void guardarPolitica(Estudiante estudiante, MultipartFile file, TiposDocumento tipoDocumento) {
        Path studentDir = Paths.get(storageDirectory).toAbsolutePath().normalize().resolve(String.valueOf(estudiante.getId()));
        String storedName = UUID.randomUUID() + ".pdf";
        Path destination = studentDir.resolve(storedName).normalize();
        if (!destination.startsWith(studentDir)) throw new BusinessException("Ruta de almacenamiento inválida");
        try {
            Files.createDirectories(studentDir);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            DocumentosEstudiante documento = new DocumentosEstudiante();
            documento.setEstudiante(estudiante);
            documento.setIdTipoDocumento(tipoDocumento);
            documento.setNombreOriginal(file.getOriginalFilename());
            documento.setNombreAlmacenado(storedName);
            documento.setUbicacionArchivo(destination.toString());
            documento.setTipoMime("application/pdf");
            documento.setTamanoBytes(file.getSize());
            documento.setSha256(sha256(destination));
            documento.setEsActual(true);
            documento.setCargadoEn(Instant.now());
            documentoEstudianteRepository.save(documento);
        } catch (IOException e) {
            try { Files.deleteIfExists(destination); } catch (IOException ignored) { }
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
        } catch (Exception e) { throw new IOException("No fue posible calcular SHA-256", e); }
    }

    private String normalizar(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " "); }
    private String normalizarNullable(String value) { String result = normalizar(value); return result.isBlank() ? null : result; }
}
