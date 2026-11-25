package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.*;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.util.ValidadorArchivo;
import cr.acusab.una.sigac.logic.exception.DuplicateResourceException;
import cr.acusab.una.sigac.logic.exception.EdicionEstudianteException;
import cr.acusab.una.sigac.logic.exception.EdicionEstudianteException.ErrorEdicion;
import cr.acusab.una.sigac.presentation.dto.request.BecaAsignarRequest;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteEdicionCompletaRequest;
import cr.acusab.una.sigac.presentation.dto.response.BecaResponse;
import cr.acusab.una.sigac.presentation.dto.response.CuentaCorreoResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteBusquedaResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteListadoResponse;
import cr.acusab.una.sigac.presentation.dto.response.UbicacionResponse;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteUpdateRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class EstudianteService {
    private static final long MAX_PDF_SIZE = 50L * 1024L * 1024L;
    private static final String MENSAJE_POLIZA_TIPO = "La póliza debe ser un PDF o una imagen (JPG, PNG o WEBP).";
    private static final List<String> GENEROS = List.of("MASCULINO", "FEMENINO", "OTRO");
    private static final List<String> PARENTESCOS = List.of("MADRE", "PADRE", "TUTOR", "FAMILIAR", "OTRO");

    private final EstudianteRepository estudianteRepository;
    private final EncargadoRepository encargadoRepository;
    private final EstudianteEncargadoRepository estudianteEncargadoRepository;
    private final InstrumentoRepository instrumentoRepository;
    private final EstudianteInstrumentoRepository estudianteInstrumentoRepository;
    private final ProfesorRepository profesorRepository;
    private final DocumentoEstudianteRepository documentoEstudianteRepository;
    private final TipoIdentificacionRepository tipoIdentificacionRepository;
    private final TipoDocumentoRepository tipoDocumentoRepository;
    // Ya no se usa para validar el centro educativo (ahora es texto libre,
    // ver obtenerCentro); se conserva por si algo más lo necesita.
    private final CentroEducativoRepository centroEducativoRepository;
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final EstadoUsuarioRepository estadoUsuarioRepository;
    private final ContrasenaTemporalService contrasenaTemporalService;
    private final BecaService becaService;
    private final InscripcionPlanEstudioService inscripcionPlanService;
    private final DistritoRepository distritoRepository;
    private final CuentaUsuarioService cuentaUsuarioService;
    private final EncargadoService encargadoService;
    private final DocumentoEstudianteService documentoEstudianteService;

    @Value("${sigac.storage.documents:./storage/documents}")
    private String storageDirectory;

    public EstudianteService(EstudianteRepository estudianteRepository,
                             EncargadoRepository encargadoRepository,
                             EstudianteEncargadoRepository estudianteEncargadoRepository,
                             InstrumentoRepository instrumentoRepository,
                             EstudianteInstrumentoRepository estudianteInstrumentoRepository,
                             ProfesorRepository profesorRepository,
                             DocumentoEstudianteRepository documentoEstudianteRepository,
                             TipoIdentificacionRepository tipoIdentificacionRepository,
                             TipoDocumentoRepository tipoDocumentoRepository,
                             CentroEducativoRepository centroEducativoRepository,
                             UsuarioRepository usuarioRepository,
                             RoleRepository roleRepository,
                             EstadoUsuarioRepository estadoUsuarioRepository,
                             ContrasenaTemporalService contrasenaTemporalService,
                             BecaService becaService,
                             InscripcionPlanEstudioService inscripcionPlanService,
                             DistritoRepository distritoRepository,
                             CuentaUsuarioService cuentaUsuarioService,
                             EncargadoService encargadoService,
                             DocumentoEstudianteService documentoEstudianteService) {
        this.cuentaUsuarioService = cuentaUsuarioService;
        this.encargadoService = encargadoService;
        this.documentoEstudianteService = documentoEstudianteService;
        this.estudianteRepository = estudianteRepository;
        this.encargadoRepository = encargadoRepository;
        this.estudianteEncargadoRepository = estudianteEncargadoRepository;
        this.instrumentoRepository = instrumentoRepository;
        this.estudianteInstrumentoRepository = estudianteInstrumentoRepository;
        this.profesorRepository = profesorRepository;
        this.documentoEstudianteRepository = documentoEstudianteRepository;
        this.tipoIdentificacionRepository = tipoIdentificacionRepository;
        this.tipoDocumentoRepository = tipoDocumentoRepository;
        this.centroEducativoRepository = centroEducativoRepository;
        this.usuarioRepository = usuarioRepository;
        this.roleRepository = roleRepository;
        this.estadoUsuarioRepository = estadoUsuarioRepository;
        this.contrasenaTemporalService = contrasenaTemporalService;
        this.becaService = becaService;
        this.inscripcionPlanService = inscripcionPlanService;
        this.distritoRepository = distritoRepository;
    }

    @Transactional
    public EstudianteResponse crear(EstudianteCreateRequest request, MultipartFile politica) {
        String identificacion = normalizar(request.numeroIdentificacion());
        TiposIdentificacion tipo = tipoIdentificacionRepository.findByIdAndActivoTrue(request.idTipoIdentificacion())
                .orElseThrow(() -> new BusinessException("El tipo de identificación indicado no existe o está inactivo"));
        ReglasIdentificacion.validar(tipo, identificacion, "");
        if (estudianteRepository.existsByIdTipoIdentificacion_IdAndNumeroIdentificacion(request.idTipoIdentificacion(), identificacion)) {
            throw new DuplicateResourceException("Ya existe un estudiante con esa identificación");
        }
        validarGenero(request.genero());
        validarPdf(politica);
        inscripcionPlanService.validarParaRegistro(request.idsPlanEstudio());
        validarEdad(request.fechaNacimiento());
        int edad = calcularEdad(request.fechaNacimiento());
        boolean menor = edad < 18;
        if (menor) {
            validarEncargados(request.encargados(), request.idTipoIdentificacion(), identificacion);
        } else if (request.encargados() != null && !request.encargados().isEmpty()) {
            throw new BusinessException("Los encargados solo se registran para menores de edad");
        }

        String centro = obtenerCentro(request.centroEducativo(), edad);
        Instrumento instrumento = instrumentoRepository.findByIdAndActivoTrue(request.idInstrumento())
                .orElseThrow(() -> new BusinessException("El instrumento indicado no existe o está inactivo"));
        Profesor profesor = obtenerProfesorParaInstrumento(request.idProfesor(), instrumento);
        TiposDocumento tipoPoliza = tipoDocumentoRepository.findByCodigoAndActivoTrue("POLIZA")
                .orElseThrow(() -> new BusinessException("No está configurado el tipo de documento POLIZA activo"));

        Distrito distrito = obtenerDistrito(request.codigoDistrito());

        Usuario usuario = crearUsuarioEstudiante(request, identificacion);

        Estudiante estudiante = new Estudiante();
        aplicarDatos(estudiante, tipo, identificacion, request.nombres(), request.primerApellido(), request.segundoApellido(), request.fechaNacimiento(),
                request.genero(), distrito, request.direccion(), request.telefono(), request.correo(), request.nivelEscolar(), centro, request.condicionesRelevantes());
        estudiante.setUsuario(usuario);
        estudiante.setFechaIngreso(LocalDate.now());
        estudiante.setActivo(true);
        Instant ahora = Instant.now();
        estudiante.setCreadoEn(ahora);
        estudiante.setActualizadoEn(ahora);
        estudiante = estudianteRepository.save(estudiante);

        crearRelacionInstrumento(estudiante, instrumento, profesor);
        List<Long> idsPlanes = inscripcionPlanService.sincronizar(estudiante.getId(), request.idsPlanEstudio());

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

        BecaResponse becaAsignada = null;
        if (request.beca() != null) {
            BecaAsignarRequest becaRequest = new BecaAsignarRequest();
            becaRequest.setIdEstudiante(estudiante.getId());
            becaRequest.setIdPeriodo(request.beca().idPeriodo());
            becaRequest.setIdTipoBeca(request.beca().idTipoBeca());
            becaRequest.setPorcentaje(request.beca().porcentaje());
            becaAsignada = becaService.asignar(becaRequest);
        }

        return respuesta(estudiante, instrumento.getId(), edad, becaAsignada, idsPlanes);
    }

    private Usuario crearUsuarioEstudiante(EstudianteCreateRequest request, String identificacion) {
        if (usuarioRepository.findByCedula(identificacion).isPresent()) {
            throw new DuplicateResourceException("Ya existe un usuario con esa identificación");
        }

        String correo = normalizarNullable(request.correo());
        if (correo == null) {
            correo = identificacion + "@estudiante.sigac.local";
        }

        Role rol = roleRepository.findByCodigoIgnoreCase("ESTUDIANTE")
                .orElseThrow(() -> new BusinessException("No está configurado el rol ESTUDIANTE"));
        EstadosUsuario estado = estadoUsuarioRepository.findAll().stream()
                .filter(x -> "ACTIVO".equalsIgnoreCase(x.getCodigo()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("No está configurado el estado ACTIVO de usuario"));

        Usuario usuario = new Usuario();
        usuario.setCedula(identificacion);
        usuario.setNombre(normalizar(request.nombres()) + " " + normalizar(request.primerApellido())
                + (normalizarNullable(request.segundoApellido()) == null ? "" : " " + normalizar(request.segundoApellido())));
        usuario.setCorreo(correo);
        contrasenaTemporalService.asignar(usuario);
        usuario.setIdRol(rol);
        usuario.setIdEstadoUsuario(estado);
        usuario.setIntentosFallidos((short) 0);
        usuario.setBloqueadoHasta(null);
        usuario.setUltimoAcceso(null);
        Instant ahora = Instant.now();
        usuario.setCreadoEn(ahora);
        usuario.setActualizadoEn(ahora);

        return usuarioRepository.save(usuario);
    }

    /*
     * Cuentas que ya usan un correo, para avisar en los formularios. Es solo
     * informativo: compartir el correo está permitido. excluirIdEstudiante
     * deja fuera la cuenta del estudiante que se está editando.
     */
    @Transactional(readOnly = true)
    public List<CuentaCorreoResponse> cuentasConCorreo(String correo, Long excluirIdEstudiante) {
        String buscado = normalizarNullable(correo);
        if (buscado == null) {
            return List.of();
        }
        Long idCuentaPropia = excluirIdEstudiante == null ? null
                : estudianteRepository.findById(excluirIdEstudiante)
                        .map(Estudiante::getUsuario)
                        .map(Usuario::getId)
                        .orElse(null);
        return usuarioRepository.findAllByCorreoIgnoreCase(buscado).stream()
                .filter(cuenta -> !Objects.equals(cuenta.getId(), idCuentaPropia))
                .sorted(Comparator.comparing(Usuario::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(cuenta -> new CuentaCorreoResponse(
                        cuenta.getNombre(),
                        ReglasIdentificacion.enmascarar(cuenta.getCedula()),
                        cuenta.getIdRol().getNombre()))
                .toList();
    }

    private static final int LIMITE_RESULTADOS_BUSQUEDA = 20;

    @Transactional(readOnly = true)
    public List<EstudianteBusquedaResponse> buscar(String texto) {
        String normalizado = normalizarNullable(texto);
        if (normalizado == null || normalizado.length() < 2) {
            return List.of();
        }
        return estudianteRepository
                .buscarPorNombreOIdentificacion(normalizado, PageRequest.of(0, LIMITE_RESULTADOS_BUSQUEDA))
                .stream()
                .map(e -> new EstudianteBusquedaResponse(e.getId(), e.getNumeroIdentificacion(), nombreCompleto(e)))
                .toList();
    }

    private String nombreCompleto(Estudiante e) {
        return String.join(
                " ",
                e.getNombres(),
                e.getPrimerApellido(),
                e.getSegundoApellido() != null ? e.getSegundoApellido() : ""
        ).trim().replaceAll("\\s+", " ");
    }

    @Transactional(readOnly = true)
    public List<EstudianteListadoResponse> listado(String texto, Boolean activo) {
        String normalizado = normalizarNullable(texto);
        List<Estudiante> estudiantes = estudianteRepository.findAll().stream()
                .filter(e -> activo == null || Objects.equals(e.getActivo(), activo))
                .filter(e -> normalizado == null
                        || normalizado.length() < 2
                        || nombreCompleto(e).toLowerCase(Locale.ROOT).contains(normalizado.toLowerCase(Locale.ROOT))
                        || e.getNumeroIdentificacion().toLowerCase(Locale.ROOT).contains(normalizado.toLowerCase(Locale.ROOT)))
                .sorted((a, b) -> nombreCompleto(a).compareToIgnoreCase(nombreCompleto(b)))
                .toList();

        return estudiantes.stream().map(e -> {
            Integer instrumentoId = estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(e.getId())
                    .stream()
                    .map(x -> x.getInstrumento().getId())
                    .findFirst()
                    .orElse(null);
            String instrumento = instrumentoId == null
                    ? null
                    : instrumentoRepository.findById(instrumentoId).map(Instrumento::getNombre).orElse(null);
            String centro = e.getIdCentroEducativo() == null ? null : e.getIdCentroEducativo().getNombre();

            return new EstudianteListadoResponse(
                    e.getId(),
                    nombreCompleto(e),
                    e.getNumeroIdentificacion(),
                    calcularEdad(e.getFechaNacimiento()),
                    e.getNivelEscolar(),
                    instrumento,
                    centro,
                    Boolean.TRUE.equals(e.getActivo())
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public EstudianteResponse obtener(Long id) {
        Estudiante estudiante = estudianteRepository.findById(id)
                .orElseThrow(() -> new BusinessException("El estudiante indicado no existe"));
        // El instrumento del estudiante es el principal; los extras no se editan aquí.
        Integer instrumentoId = estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(id).stream()
                .min(Comparator.comparing((EstudiantesInstrumento x) -> !Boolean.TRUE.equals(x.getEsPrincipal())))
                .map(x -> x.getInstrumento().getId()).orElse(null);
        return respuesta(estudiante, instrumentoId, calcularEdad(estudiante.getFechaNacimiento()), null,
                inscripcionPlanService.idsPlanesActivos(id));
    }

    @Transactional
    public EstudianteResponse actualizar(Long id, EstudianteUpdateRequest request) {
        Estudiante estudiante = estudianteRepository.findById(id)
                .orElseThrow(() -> new BusinessException("El estudiante indicado no existe"));
        String identificacion = normalizar(request.numeroIdentificacion());
        TiposIdentificacion tipo = validarIdentificacionEstudiante(id, request.idTipoIdentificacion(), identificacion);
        validarGenero(request.genero());
        validarEdad(request.fechaNacimiento());
        int edad = calcularEdad(request.fechaNacimiento());
        // Si con la nueva fecha queda menor de edad, debe tener quien lo represente.
        // (Si pasa a ser mayor, sus encargados se conservan.)
        if (edad < 18 && estudianteEncargadoRepository.countByIdEstudiante(id) == 0) {
            throw new BusinessException("Un menor de edad debe tener al menos un encargado registrado.");
        }
        String centro = obtenerCentro(request.centroEducativo(), edad);
        Instrumento instrumento = instrumentoRepository.findByIdAndActivoTrue(request.idInstrumento())
                .orElseThrow(() -> new BusinessException("El instrumento indicado no existe o está inactivo"));
        validarInstrumentoNoEsExtra(id, instrumento);
        Distrito distrito = obtenerDistrito(request.codigoDistrito());

        // La cuenta de usuario ingresa con la identificación y recupera la
        // contraseña con el correo: se mantienen iguales a los del estudiante.
        cuentaUsuarioService.sincronizarIdentidad(estudiante.getUsuario(), identificacion, normalizarNullable(request.correo()));

        aplicarDatos(estudiante, tipo, identificacion, request.nombres(), request.primerApellido(), request.segundoApellido(), request.fechaNacimiento(),
                request.genero(), distrito, request.direccion(), request.telefono(), request.correo(), request.nivelEscolar(), centro, request.condicionesRelevantes());
        estudiante.setActualizadoEn(Instant.now());
        estudianteRepository.save(estudiante);

        var principal = relacionPrincipal(id);
        if (principal == null) crearRelacionInstrumento(estudiante, instrumento, null);
        else { principal.setInstrumento(instrumento); estudianteInstrumentoRepository.save(principal); }
        List<Long> idsPlanes = inscripcionPlanService.sincronizar(id, request.idsPlanEstudio());
        return respuesta(estudiante, instrumento.getId(), edad, null, idsPlanes);
    }

    /*
     * Guarda toda la pantalla "Editar estudiante". Primero revisa todo sin
     * escribir, para informar todos los errores juntos. Cualquier excepción
     * revierte la transacción: se guarda todo o no se guarda nada.
     */
    @Transactional
    public EstudianteResponse actualizarCompleto(Long id, EstudianteEdicionCompletaRequest edicion,
                                                 MultipartFile poliza, LocalDate polizaEmision,
                                                 LocalDate polizaVencimiento) {
        Estudiante estudiante = estudianteRepository.findById(id)
                .orElseThrow(() -> new BusinessException("El estudiante indicado no existe"));
        EstudianteUpdateRequest datos = edicion.estudiante();
        String identificacion = normalizar(datos.numeroIdentificacion());
        String correo = normalizarNullable(datos.correo());
        Usuario cuenta = estudiante.getUsuario();
        List<ErrorEdicion> errores = new ArrayList<>();

        // --- Datos personales
        TiposIdentificacion tipo = enSeccion(errores, EdicionEstudianteException.PERSONAL,
                () -> validarIdentificacionEstudiante(id, datos.idTipoIdentificacion(), identificacion));
        enSeccion(errores, EdicionEstudianteException.PERSONAL, () -> {
            cuentaUsuarioService.validarCedulaDisponible(cuenta, identificacion);
            return true;
        });
        enSeccion(errores, EdicionEstudianteException.PERSONAL, () -> {
            validarGenero(datos.genero());
            return true;
        });
        Integer edad = enSeccion(errores, EdicionEstudianteException.PERSONAL, () -> {
            validarEdad(datos.fechaNacimiento());
            return calcularEdad(datos.fechaNacimiento());
        });

        // --- Contacto y residencia
        Distrito distrito = enSeccion(errores, EdicionEstudianteException.CONTACTO,
                () -> obtenerDistrito(datos.codigoDistrito()));

        // --- Información académica
        String centro = edad == null ? null : enSeccion(errores, EdicionEstudianteException.ACADEMICO,
                () -> Objects.toString(obtenerCentro(datos.centroEducativo(), edad), ""));
        enSeccion(errores, EdicionEstudianteException.ACADEMICO, () -> {
            inscripcionPlanService.validarParaActualizacion(id, datos.idsPlanEstudio());
            return true;
        });

        // --- Instrumento principal y su profesor
        Instrumento instrumento = enSeccion(errores, EdicionEstudianteException.INSTRUMENTOS, () -> {
            Instrumento elegido = instrumentoRepository.findByIdAndActivoTrue(datos.idInstrumento())
                    .orElseThrow(() -> new BusinessException("El instrumento indicado no existe o está inactivo"));
            validarInstrumentoNoEsExtra(id, elegido);
            return elegido;
        });
        Profesor profesor = instrumento == null ? null : enSeccion(errores, EdicionEstudianteException.INSTRUMENTOS,
                () -> obtenerProfesorParaInstrumento(edicion.idProfesor(), instrumento));

        // --- Encargados: solo para menores; a un mayor no se le tocan
        boolean menor = edad != null && edad < 18;
        if (menor) {
            errores.addAll(encargadoService.validarEdicion(id, datos.idTipoIdentificacion(), identificacion,
                    edicion.encargados()));
        }

        // --- Póliza
        boolean traePoliza = poliza != null && !poliza.isEmpty();
        TiposDocumento tipoPoliza = !traePoliza ? null : enSeccion(errores, EdicionEstudianteException.POLIZA, () -> {
            if (!Boolean.TRUE.equals(estudiante.getActivo())) {
                throw new BusinessException("No se puede adjuntar la póliza de un estudiante inactivo.");
            }
            documentoEstudianteService.validarAdjuntoPoliza(poliza, polizaEmision, polizaVencimiento);
            return tipoDocumentoRepository.findByCodigoAndActivoTrue("POLIZA")
                    .orElseThrow(() -> new BusinessException("No está configurado el tipo de documento POLIZA activo"));
        });
        if (!traePoliza && (polizaEmision != null || polizaVencimiento != null)) {
            errores.add(new ErrorEdicion(EdicionEstudianteException.POLIZA,
                    "Para registrar las fechas de la póliza, adjunte el archivo."));
        }

        if (!errores.isEmpty()) {
            throw new EdicionEstudianteException(errores);
        }

        // --- Todo es válido: se guarda
        try {
            cuentaUsuarioService.sincronizarIdentidad(cuenta, identificacion, correo);
            aplicarDatos(estudiante, tipo, identificacion, datos.nombres(), datos.primerApellido(),
                    datos.segundoApellido(), datos.fechaNacimiento(), datos.genero(), distrito, datos.direccion(),
                    datos.telefono(), datos.correo(), datos.nivelEscolar(), normalizarNullable(centro),
                    datos.condicionesRelevantes());
            estudiante.setFechaIngreso(edicion.fechaIngreso());
            estudiante.setActualizadoEn(Instant.now());
            estudianteRepository.save(estudiante);

            EstudiantesInstrumento principal = relacionPrincipal(id);
            if (principal == null) {
                crearRelacionInstrumento(estudiante, instrumento, profesor);
            } else {
                principal.setInstrumento(instrumento);
                principal.setProfesor(profesor);
                estudianteInstrumentoRepository.save(principal);
            }

            List<Long> idsPlanes = inscripcionPlanService.sincronizar(id, datos.idsPlanEstudio());

            if (menor) {
                aplicarEnSeccion(EdicionEstudianteException.ENCARGADOS,
                        () -> encargadoService.aplicarEdicion(estudiante, edicion.encargados()));
            }
            if (traePoliza) {
                aplicarEnSeccion(EdicionEstudianteException.POLIZA,
                        () -> documentoEstudianteService.adjuntarOReemplazar(
                                id, tipoPoliza.getId(), poliza, polizaEmision, polizaVencimiento));
            }

            // Fuerza los INSERT/UPDATE aquí para reportar un choque de datos como error de la pantalla.
            estudianteRepository.flush();
            return respuesta(estudiante, instrumento.getId(), edad, null, idsPlanes);
        } catch (DataIntegrityViolationException e) {
            throw new EdicionEstudianteException(List.of(new ErrorEdicion(EdicionEstudianteException.PERSONAL,
                    "No se guardó porque otro registro ya usa alguno de estos datos. Revise la identificación.")));
        }
    }

    // Ejecuta una validación y, si falla, anota el error en su sección.
    private <T> T enSeccion(List<ErrorEdicion> errores, String seccion, Supplier<T> validacion) {
        try {
            return validacion.get();
        } catch (RuntimeException e) {
            errores.add(new ErrorEdicion(seccion, mensajeDeNegocio(e)));
            return null;
        }
    }

    private void aplicarEnSeccion(String seccion, Runnable paso) {
        try {
            paso.run();
        } catch (BusinessException | DuplicateResourceException | ResponseStatusException e) {
            throw new EdicionEstudianteException(List.of(new ErrorEdicion(seccion, mensajeDeNegocio(e))));
        }
    }

    // Solo los errores previstos se muestran; uno inesperado sigue su camino (500).
    private String mensajeDeNegocio(RuntimeException e) {
        if (e instanceof ResponseStatusException estado) return estado.getReason();
        if (e instanceof BusinessException || e instanceof DuplicateResourceException
                || e instanceof IllegalArgumentException) {
            return e.getMessage();
        }
        throw e;
    }

    private TiposIdentificacion validarIdentificacionEstudiante(Long id, Integer idTipo, String identificacion) {
        TiposIdentificacion tipo = tipoIdentificacionRepository.findByIdAndActivoTrue(idTipo)
                .orElseThrow(() -> new BusinessException("El tipo de identificación indicado no existe o está inactivo"));
        ReglasIdentificacion.validar(tipo, identificacion, "");
        var existente = estudianteRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(idTipo, identificacion);
        if (existente.isPresent() && !Objects.equals(existente.get().getId(), id)) {
            throw new DuplicateResourceException("Ya existe un estudiante con esa identificación");
        }
        return tipo;
    }

    // La relación vigente marcada como principal; las extra nunca se modifican aquí.
    private EstudiantesInstrumento relacionPrincipal(Long idEstudiante) {
        return estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(idEstudiante).stream()
                .filter(x -> Boolean.TRUE.equals(x.getEsPrincipal()))
                .min(Comparator.comparing(EstudiantesInstrumento::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    // Un mismo instrumento no puede quedar vigente dos veces.
    private void validarInstrumentoNoEsExtra(Long idEstudiante, Instrumento instrumento) {
        boolean yaEsExtra = estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(idEstudiante).stream()
                .anyMatch(x -> !Boolean.TRUE.equals(x.getEsPrincipal())
                        && Objects.equals(x.getInstrumento().getId(), instrumento.getId()));
        if (yaEsExtra) {
            throw new BusinessException("El estudiante ya tiene " + instrumento.getNombre() + " como instrumento extra.");
        }
    }

    private void aplicarDatos(Estudiante e, TiposIdentificacion tipo, String identificacion, String nombres,
                              String primerApellido, String segundoApellido, LocalDate nacimiento, String genero,
                              Distrito distrito, String direccion, String telefono, String correo, String nivel,
                              String centro, String condiciones) {
        e.setIdTipoIdentificacion(tipo);
        e.setNumeroIdentificacion(identificacion);
        e.setNombres(normalizar(nombres));
        e.setPrimerApellido(normalizar(primerApellido));
        e.setSegundoApellido(normalizarNullable(segundoApellido));
        e.setFechaNacimiento(nacimiento);
        e.setGenero(normalizar(genero).toUpperCase(Locale.ROOT));
        e.setDistrito(distrito);
        e.setDireccion(normalizar(direccion));
        e.setTelefono(normalizarNullable(telefono));
        e.setCorreo(normalizar(correo));
        e.setNivelEscolar(normalizar(nivel));
        e.setCentroEducativo(centro);
        e.setCondicionesRelevantes(normalizarNullable(condiciones));
    }

    private Distrito obtenerDistrito(Integer codigoDistrito) {
        if (codigoDistrito == null) throw new BusinessException("El distrito es obligatorio");
        return distritoRepository.findById(codigoDistrito)
                .orElseThrow(() -> new BusinessException("El distrito indicado no existe"));
    }

    private void crearRelacionInstrumento(Estudiante estudiante, Instrumento instrumento, Profesor profesor) {
        EstudiantesInstrumento relacion = new EstudiantesInstrumento();
        relacion.setEstudiante(estudiante);
        relacion.setInstrumento(instrumento);
        relacion.setProfesor(profesor);
        relacion.setFechaInicio(LocalDate.now());
        // Solo se llama cuando el estudiante no tiene instrumentos vigentes:
        // el primero siempre es el instrumento del plan.
        relacion.setEsPrincipal(true);
        estudianteInstrumentoRepository.save(relacion);
    }

    // HU-026 - El profesor es opcional, pero si se indica debe existir,
    // estar activo y tener ese instrumento entre sus especialidades
    // (campo de texto separado por comas). Misma regla usada al asignar
    // instrumentos desde la pantalla de Director Académico.
    private Profesor obtenerProfesorParaInstrumento(Long idProfesor, Instrumento instrumento) {
        if (idProfesor == null) {
            return null;
        }

        Profesor profesor = profesorRepository.findById(idProfesor)
                .orElseThrow(() -> new BusinessException("El profesor indicado no existe."));

        if (!Boolean.TRUE.equals(profesor.getActivo())) {
            throw new BusinessException("El profesor indicado no está activo.");
        }

        String especialidad = profesor.getEspecialidad();
        boolean tieneEspecialidad = especialidad != null
                && especialidad.toLowerCase().contains(instrumento.getNombre().toLowerCase());

        if (!tieneEspecialidad) {
            throw new BusinessException(
                    "El profesor seleccionado no tiene " + instrumento.getNombre() + " como especialidad.");
        }

        return profesor;
    }

    private EstudianteResponse respuesta(Estudiante e, Integer instrumentoId, int edad, BecaResponse beca,
                                         List<Long> idsPlanEstudio) {
        return new EstudianteResponse(e.getId(), e.getNumeroIdentificacion(), e.getNombres(), e.getPrimerApellido(),
                e.getSegundoApellido(), e.getFechaNacimiento(), edad, e.getGenero(), e.getDireccion(),
                UbicacionResponse.de(e.getDistrito()), e.getTelefono(),
                e.getCorreo(), e.getNivelEscolar(), e.getCentroEducativo(),
                instrumentoId, edad < 18, beca, idsPlanEstudio,
                e.getIdTipoIdentificacion() == null ? null : e.getIdTipoIdentificacion().getId(),
                e.getCondicionesRelevantes());
    }

    private int calcularEdad(LocalDate nacimiento) {
        if (nacimiento == null) throw new BusinessException("La fecha de nacimiento es obligatoria");
        return Period.between(nacimiento, LocalDate.now()).getYears();
    }

    // Edad permitida: de 2 a 100 años (el frontend aplica la misma regla).
    private static final int EDAD_MINIMA = 2;
    private static final int EDAD_MAXIMA = 100;

    private void validarEdad(LocalDate nacimiento) {
        int edad = calcularEdad(nacimiento);
        if (edad < EDAD_MINIMA) throw new BusinessException("El estudiante debe tener al menos 2 años.");
        if (edad > EDAD_MAXIMA) throw new BusinessException("Revise la fecha de nacimiento.");
    }

    private boolean requiereCentroEducativo(int edad) { return edad >= 6 && edad < 18; }

    private String obtenerCentro(String centroEducativo, int edad) {
        String normalizado = normalizarNullable(centroEducativo);
        if (requiereCentroEducativo(edad) && normalizado == null) {
            throw new BusinessException("Debe indicar el centro educativo para esta edad");
        }
        if (!requiereCentroEducativo(edad) && normalizado != null) {
            throw new BusinessException("El centro educativo no corresponde para esta edad");
        }
        return normalizado;
    }

    private Encargado obtenerOCrearEncargado(EstudianteCreateRequest.EncargadoRequest data) {
        String identificacion = normalizarNullable(data.numeroIdentificacion());
        if (data.idTipoIdentificacion() != null && identificacion != null) {
            var existente = encargadoRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(data.idTipoIdentificacion(), identificacion);
            if (existente.isPresent()) {
                // Se reutiliza el encargado ya registrado solo si es la misma persona.
                Encargado encargado = existente.get();
                EncargadoService.validarMismaPersona(encargado, data.nombres(), data.primerApellido());
                return encargado;
            }
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

    private void validarEncargados(List<EstudianteCreateRequest.EncargadoRequest> encargados,
                                   Integer idTipoEstudiante, String identificacionEstudiante) {
        if (encargados == null || encargados.isEmpty() || encargados.size() > 2) throw new BusinessException("Un menor debe tener uno o dos encargados");
        long principales = encargados.stream().filter(EstudianteCreateRequest.EncargadoRequest::responsablePrincipal).count();
        long emergencias = encargados.stream().filter(EstudianteCreateRequest.EncargadoRequest::contactoEmergencia).count();
        if (principales != 1) throw new BusinessException("Debe existir exactamente un responsable principal");
        if (emergencias > 2) throw new BusinessException("No puede haber más de dos contactos de emergencia");

        Set<String> identificaciones = new HashSet<>();
        for (var encargado : encargados) {
            if (!PARENTESCOS.contains(normalizar(encargado.parentesco()).toUpperCase(Locale.ROOT))) throw new BusinessException("El parentesco no es válido");

            // La identificación del encargado es opcional, pero va completa (tipo y número) o vacía.
            String numero = normalizarNullable(encargado.numeroIdentificacion());
            Integer idTipo = encargado.idTipoIdentificacion();
            if (numero == null && idTipo == null) continue;
            if (numero == null) throw new BusinessException("Indique el número de identificación del encargado.");
            if (idTipo == null) throw new BusinessException("Indique el tipo de identificación del encargado.");

            TiposIdentificacion tipo = tipoIdentificacionRepository.findByIdAndActivoTrue(idTipo)
                    .orElseThrow(() -> new BusinessException("El tipo de identificación del encargado no existe o está inactivo"));
            ReglasIdentificacion.validar(tipo, numero, "del encargado");

            if (Objects.equals(idTipo, idTipoEstudiante) && numero.equalsIgnoreCase(identificacionEstudiante)) {
                throw new BusinessException("El encargado no puede tener la misma identificación que el estudiante.");
            }
            // Sin esta regla, el segundo encargado sobrescribe la relación del primero
            // y el estudiante puede quedar sin responsable principal.
            if (!identificaciones.add(idTipo + ":" + numero.toUpperCase(Locale.ROOT))) {
                throw new BusinessException("Los dos encargados no pueden tener la misma identificación.");
            }
        }
    }

    private void validarGenero(String genero) {
        if (!GENEROS.contains(normalizar(genero).toUpperCase(Locale.ROOT))) throw new BusinessException("El género seleccionado no es válido");
    }

    /*
     * Póliza: PDF o imagen (JPG, PNG o WEBP), hasta 50 MB.
     * - PDF: la misma validación de siempre (extensión .pdf y encabezado %PDF-).
     * - Imagen: sus primeros bytes deben ser los de ese formato.
     */
    private void validarPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("La póliza es obligatoria");
        if (file.getSize() > MAX_PDF_SIZE) throw new BusinessException("La póliza no puede superar 50 MB");
        String type = ValidadorArchivo.tipoMime(file);
        if (!ValidadorArchivo.esPdfOImagen(type)) throw new BusinessException(MENSAJE_POLIZA_TIPO);
        if (ValidadorArchivo.PDF.equals(type)) {
            String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
            if (!name.endsWith(".pdf")) throw new BusinessException(MENSAJE_POLIZA_TIPO);
            try (InputStream input = file.getInputStream()) {
                byte[] header = input.readNBytes(5);
                if (header.length < 5 || !new String(header).equals("%PDF-")) throw new BusinessException("El archivo no es un PDF válido");
            } catch (IOException e) { throw new BusinessException("No fue posible leer la póliza"); }
            return;
        }
        try {
            if (!ValidadorArchivo.contenidoCoincide(file, type)) throw new BusinessException(MENSAJE_POLIZA_TIPO);
        } catch (IOException e) { throw new BusinessException("No fue posible leer la póliza"); }
    }

    private void guardarPolitica(Estudiante estudiante, MultipartFile file, TiposDocumento tipoDocumento) {
        Path studentDir = Paths.get(storageDirectory).toAbsolutePath().normalize().resolve(String.valueOf(estudiante.getId()));
        // Ya validado por validarPdf: PDF o imagen, con su extensión y tipo MIME reales.
        String tipoMime = ValidadorArchivo.tipoMime(file);
        String storedName = ValidadorArchivo.nombreAlmacenado(tipoMime);
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
            documento.setTipoMime(tipoMime);
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
