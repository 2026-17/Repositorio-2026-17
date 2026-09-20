package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Role;
import cr.acusab.una.sigac.data.entity.EstadosUsuario;
import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.entity.TiposIdentificacion;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.EstadoUsuarioRepository;
import cr.acusab.una.sigac.data.repository.InstrumentoRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.data.repository.RoleRepository;
import cr.acusab.una.sigac.data.repository.TipoIdentificacionRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.presentation.dto.request.ProfesorCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.ProfesorUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.request.UsuarioCreateRequest;
import cr.acusab.una.sigac.presentation.dto.response.ProfesorResponse;
import cr.acusab.una.sigac.presentation.dto.response.TipoIdentificacionResponse;
import cr.acusab.una.sigac.presentation.dto.response.UsuarioResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class ProfesorService {

    // Codigos fijos del catalogo (ver seed en sigac_db.sql): un profesor
    // siempre se crea con el rol PROFESOR y la cuenta arranca ACTIVA.
    private static final String CODIGO_ROL_PROFESOR = "PROFESOR";
    private static final String CODIGO_ESTADO_USUARIO_ACTIVO = "ACTIVO";

    private final ProfesorRepository profesorRepository;
    private final TipoIdentificacionRepository tipoIdentificacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final EstadoUsuarioRepository estadoUsuarioRepository;
    private final UsuarioService usuarioService;
    private final InstrumentoRepository instrumentoRepository;

    public ProfesorService(
            ProfesorRepository profesorRepository,
            TipoIdentificacionRepository tipoIdentificacionRepository,
            UsuarioRepository usuarioRepository,
            RoleRepository roleRepository,
            EstadoUsuarioRepository estadoUsuarioRepository,
            UsuarioService usuarioService,
            InstrumentoRepository instrumentoRepository
    ) {
        this.profesorRepository = profesorRepository;
        this.tipoIdentificacionRepository = tipoIdentificacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.roleRepository = roleRepository;
        this.estadoUsuarioRepository = estadoUsuarioRepository;
        this.usuarioService = usuarioService;
        this.instrumentoRepository = instrumentoRepository;
    }

    @Transactional
    public ProfesorResponse registrar(ProfesorCreateRequest request, UsuarioCreateRequest usuarioRequest) {
        Role rolProfesor = roleRepository.findByCodigoIgnoreCase(CODIGO_ROL_PROFESOR)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No existe el rol PROFESOR en el catalogo"));
        EstadosUsuario estadoActivo = estadoUsuarioRepository.findByCodigoIgnoreCase(CODIGO_ESTADO_USUARIO_ACTIVO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No existe el estado ACTIVO en el catalogo"));

        // El profesor siempre se crea con rol PROFESOR y estado ACTIVO,
        // sin importar lo que venga (o no) en el request.
        usuarioRequest.setIdRol(rolProfesor.getId());
        usuarioRequest.setIdEstadoUsuario(estadoActivo.getId());

        UsuarioResponse usuarioCreado = usuarioService.crearUsuario(usuarioRequest);
        Usuario usuario = usuarioRepository.findById(usuarioCreado.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo recuperar el usuario recien creado"));

        TiposIdentificacion tipo = tipoIdentificacionRepository.findById(request.getIdTipoIdentificacion())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de identificacion no existe"));

        profesorRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                        request.getIdTipoIdentificacion(), request.getNumeroIdentificacion())
                .ifPresent(p -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un profesor registrado con esa identificacion");
                });

        Instant now = Instant.now();
        Profesor profesor = new Profesor();
        profesor.setIdUsuario(usuario);
        profesor.setIdTipoIdentificacion(tipo);
        profesor.setNumeroIdentificacion(request.getNumeroIdentificacion());
        profesor.setNombres(request.getNombres());
        profesor.setPrimerApellido(request.getPrimerApellido());
        profesor.setSegundoApellido(request.getSegundoApellido());
        profesor.setTelefono(request.getTelefono());
        profesor.setCorreo(request.getCorreo());
        profesor.setDireccion(request.getDireccion());
        profesor.setGradoAcademico(request.getGradoAcademico());
        profesor.setEspecialidad(request.getEspecialidad());
        profesor.setEmpleador(request.getEmpleador());
        profesor.setClasePuesto(request.getClasePuesto());
        profesor.setFechaIngreso(request.getFechaIngreso() != null ? request.getFechaIngreso() : LocalDate.now());
        profesor.setActivo(true);
        profesor.setIdUsuarioUltimaModificacion(usuarioActual());
        profesor.setCreadoEn(now);
        profesor.setActualizadoEn(now);

        Profesor guardado = profesorRepository.save(profesor);
        return toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<ProfesorResponse> listar(Boolean activo) {
        List<Profesor> profesores = activo != null
                ? profesorRepository.findByActivo(activo)
                : profesorRepository.findAll();
        return profesores.stream().map(this::toResponse).toList();
    }

    // HU-026 - Profesores activos cuya especialidad incluye el instrumento
    // indicado (usado para elegir profesor al asignar un instrumento).
    @Transactional(readOnly = true)
    public List<ProfesorResponse> listarPorInstrumento(Integer idInstrumento) {
        Instrumento instrumento = instrumentoRepository.findById(idInstrumento)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrumento no encontrado"));

        return profesorRepository
                .findByActivoTrueAndEspecialidadContainingIgnoreCase(instrumento.getNombre())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfesorResponse obtener(Long id) {
        return toResponse(buscarOFallar(id));
    }

    @Transactional
    public ProfesorResponse modificar(Long id, ProfesorUpdateRequest request) {
        Profesor profesor = buscarOFallar(id);

        TiposIdentificacion tipo = tipoIdentificacionRepository.findById(request.getIdTipoIdentificacion())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de identificacion no existe"));

        boolean cambioIdentificacion =
                !request.getIdTipoIdentificacion().equals(profesor.getIdTipoIdentificacion().getId())
                        || !request.getNumeroIdentificacion().equals(profesor.getNumeroIdentificacion());

        if (cambioIdentificacion) {
            profesorRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                            request.getIdTipoIdentificacion(), request.getNumeroIdentificacion())
                    .ifPresent(p -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe otro profesor con esa identificacion");
                    });
        }

        profesor.setIdTipoIdentificacion(tipo);
        profesor.setNumeroIdentificacion(request.getNumeroIdentificacion());
        profesor.setNombres(request.getNombres());
        profesor.setPrimerApellido(request.getPrimerApellido());
        profesor.setSegundoApellido(request.getSegundoApellido());
        profesor.setTelefono(request.getTelefono());
        profesor.setCorreo(request.getCorreo());
        profesor.setDireccion(request.getDireccion());
        profesor.setGradoAcademico(request.getGradoAcademico());
        profesor.setEspecialidad(request.getEspecialidad());
        profesor.setEmpleador(request.getEmpleador());
        profesor.setClasePuesto(request.getClasePuesto());
        if (request.getFechaIngreso() != null) {
            profesor.setFechaIngreso(request.getFechaIngreso());
        }
        // fecha_salida ya no se edita desde el formulario: la controlan
        // exclusivamente darDeBaja (la fija) y reactivar (la vacia).
        profesor.setIdUsuarioUltimaModificacion(usuarioActual());
        profesor.setActualizadoEn(Instant.now());

        return toResponse(profesorRepository.save(profesor));
    }

    // Baja logica: conserva el historial institucional (no se borra de la BD)
    @Transactional
    public ProfesorResponse darDeBaja(Long id) {
        Profesor profesor = buscarOFallar(id);
        profesor.setActivo(false);
        profesor.setFechaSalida(LocalDate.now());
        profesor.setIdUsuarioUltimaModificacion(usuarioActual());
        profesor.setActualizadoEn(Instant.now());
        return toResponse(profesorRepository.save(profesor));
    }

    // Reactivacion: permite que un profesor vuelva a impartir cursos
    @Transactional
    public ProfesorResponse reactivar(Long id) {
        Profesor profesor = buscarOFallar(id);
        profesor.setActivo(true);
        profesor.setFechaSalida(null);
        profesor.setIdUsuarioUltimaModificacion(usuarioActual());
        profesor.setActualizadoEn(Instant.now());
        return toResponse(profesorRepository.save(profesor));
    }

    // Eliminacion fisica, pedida explicitamente en los criterios de aceptacion
    @Transactional
    public void eliminar(Long id) {
        Profesor profesor = buscarOFallar(id);
        profesorRepository.delete(profesor);
    }

    private Profesor buscarOFallar(Long id) {
        return profesorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profesor no encontrado"));
    }

    // Resuelve el usuario autenticado (username = cedula, ver UsuarioDetailsService)
    // para completar id_usuario_ultima_modificacion, usado por los triggers de bitacora.
    private Usuario usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return usuarioRepository.findByCedula(auth.getName()).orElse(null);
    }

    private ProfesorResponse toResponse(Profesor p) {
        TiposIdentificacion tipo = p.getIdTipoIdentificacion();
        return new ProfesorResponse(
                p.getId(),
                new TipoIdentificacionResponse(tipo.getId(), tipo.getCodigo(), tipo.getNombre()),
                p.getNumeroIdentificacion(),
                p.getNombres(),
                p.getPrimerApellido(),
                p.getSegundoApellido(),
                p.getTelefono(),
                p.getCorreo(),
                p.getDireccion(),
                p.getGradoAcademico(),
                p.getEspecialidad(),
                p.getEmpleador(),
                p.getClasePuesto(),
                p.getFechaIngreso(),
                p.getFechaSalida(),
                p.getActivo(),
                p.getCreadoEn(),
                p.getActualizadoEn()
        );
    }
}
