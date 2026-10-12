package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Distrito;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Role;
import cr.acusab.una.sigac.data.entity.EstadosUsuario;
import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.entity.TiposIdentificacion;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.*;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.request.ProfesorCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.ProfesorUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.request.UsuarioCreateRequest;
import cr.acusab.una.sigac.presentation.dto.response.ProfesorResponse;
import cr.acusab.una.sigac.presentation.dto.response.TipoIdentificacionResponse;
import cr.acusab.una.sigac.presentation.dto.response.UbicacionResponse;
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
    private final DistritoRepository distritoRepository;
    private final CuentaUsuarioService cuentaUsuarioService;

    public ProfesorService(
            ProfesorRepository profesorRepository,
            TipoIdentificacionRepository tipoIdentificacionRepository,
            UsuarioRepository usuarioRepository,
            RoleRepository roleRepository,
            EstadoUsuarioRepository estadoUsuarioRepository,
            UsuarioService usuarioService,
            InstrumentoRepository instrumentoRepository,
            DistritoRepository distritoRepository,
            CuentaUsuarioService cuentaUsuarioService
    ) {
        this.cuentaUsuarioService = cuentaUsuarioService;
        this.profesorRepository = profesorRepository;
        this.tipoIdentificacionRepository = tipoIdentificacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.roleRepository = roleRepository;
        this.estadoUsuarioRepository = estadoUsuarioRepository;
        this.usuarioService = usuarioService;
        this.instrumentoRepository = instrumentoRepository;
        this.distritoRepository = distritoRepository;
    }

    @Transactional
    public ProfesorResponse registrar(ProfesorCreateRequest request, UsuarioCreateRequest usuarioRequest) {
        TiposIdentificacion tipo = tipoIdentificacionRepository.findById(request.getIdTipoIdentificacion())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de identificacion no existe"));

        request.setNumeroIdentificacion(request.getNumeroIdentificacion().trim());
        ReglasIdentificacion.validar(tipo, request.getNumeroIdentificacion(), "");
        validarFechaIngreso(request.getFechaIngreso());

        profesorRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                        request.getIdTipoIdentificacion(), request.getNumeroIdentificacion())
                .ifPresent(p -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un profesor registrado con esa identificacion");
                });

        Role rolProfesor = roleRepository.findByCodigoIgnoreCase(CODIGO_ROL_PROFESOR)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No existe el rol PROFESOR en el catalogo"));
        EstadosUsuario estadoActivo = estadoUsuarioRepository.findByCodigoIgnoreCase(CODIGO_ESTADO_USUARIO_ACTIVO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No existe el estado ACTIVO en el catalogo"));

        // El profesor siempre se crea con rol PROFESOR y estado ACTIVO, sin
        // importar lo que venga (o no) en la solicitud. La cuenta se crea despues
        // de validar al profesor, y todo va en una sola transaccion: si algo
        // falla no queda una cuenta huerfana.
        usuarioRequest.setIdRol(rolProfesor.getId());
        usuarioRequest.setIdEstadoUsuario(estadoActivo.getId());

        UsuarioResponse usuarioCreado = usuarioService.crearUsuario(usuarioRequest);
        Usuario usuario = usuarioRepository.findById(usuarioCreado.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo recuperar el usuario recien creado"));

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
        profesor.setDistrito(obtenerDistrito(request.getCodigoDistrito()));
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

        String identificacion = request.getNumeroIdentificacion().trim();
        ReglasIdentificacion.validar(tipo, identificacion, "");
        validarFechaIngreso(request.getFechaIngreso());

        boolean cambioIdentificacion =
                !request.getIdTipoIdentificacion().equals(profesor.getIdTipoIdentificacion().getId())
                        || !identificacion.equals(profesor.getNumeroIdentificacion());

        if (cambioIdentificacion) {
            profesorRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                            request.getIdTipoIdentificacion(), identificacion)
                    .filter(p -> !p.getId().equals(profesor.getId()))
                    .ifPresent(p -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe otro profesor con esa identificacion");
                    });
        }

        // La cuenta de usuario usa la identificación como cédula de ingreso y
        // el correo para recuperar la contraseña: se mantienen iguales.
        String correo = request.getCorreo() == null || request.getCorreo().isBlank()
                ? null
                : request.getCorreo().trim();
        cuentaUsuarioService.sincronizarIdentidad(profesor.getIdUsuario(), identificacion, correo);

        profesor.setIdTipoIdentificacion(tipo);
        profesor.setNumeroIdentificacion(identificacion);
        profesor.setNombres(request.getNombres());
        profesor.setPrimerApellido(request.getPrimerApellido());
        profesor.setSegundoApellido(request.getSegundoApellido());
        profesor.setTelefono(request.getTelefono());
        profesor.setCorreo(correo);
        profesor.setDistrito(obtenerDistrito(request.getCodigoDistrito()));
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
        // Sin acceso al sistema mientras esté dado de baja
        cuentaUsuarioService.inactivar(profesor.getIdUsuario());
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
        cuentaUsuarioService.activar(profesor.getIdUsuario());
        return toResponse(profesorRepository.save(profesor));
    }

    private void validarFechaIngreso(LocalDate fechaIngreso) {
        if (fechaIngreso != null && fechaIngreso.isAfter(LocalDate.now())) {
            throw new BusinessException("La fecha de ingreso no puede ser futura.");
        }
    }

    // Eliminacion fisica, pedida explicitamente en los criterios de aceptacion
    @Transactional
    public void eliminar(Long id) {
        Profesor profesor = buscarOFallar(id);
        profesorRepository.delete(profesor);
    }

    // El distrito es opcional (igual que la direccion), pero si viene debe existir
    private Distrito obtenerDistrito(Integer codigoDistrito) {
        if (codigoDistrito == null) {
            return null;
        }
        return distritoRepository.findById(codigoDistrito)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El distrito indicado no existe"));
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
                UbicacionResponse.de(p.getDistrito()),
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
