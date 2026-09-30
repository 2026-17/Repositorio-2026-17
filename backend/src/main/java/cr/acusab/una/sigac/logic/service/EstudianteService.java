package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.TipoIdentificacion;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.TipoIdentificacionRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteCreateRequest;
import cr.acusab.una.sigac.presentation.dto.request.EstudianteUpdateRequest;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteResponse;
import cr.acusab.una.sigac.presentation.dto.response.TipoIdentificacionResponse;
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
public class EstudianteService {

    private final EstudianteRepository estudianteRepository;
    private final TipoIdentificacionRepository tipoIdentificacionRepository;
    private final UsuarioRepository usuarioRepository;

    public EstudianteService(
            EstudianteRepository estudianteRepository,
            TipoIdentificacionRepository tipoIdentificacionRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.estudianteRepository = estudianteRepository;
        this.tipoIdentificacionRepository = tipoIdentificacionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public EstudianteResponse registrar(EstudianteCreateRequest request) {
        TipoIdentificacion tipo = tipoIdentificacionRepository.findById(request.getIdTipoIdentificacion())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de identificacion no existe"));

        estudianteRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                        request.getIdTipoIdentificacion(), request.getNumeroIdentificacion())
                .ifPresent(e -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un estudiante registrado con esa identificacion");
                });

        Usuario usuario = null;
        if (request.getIdUsuario() != null) {
            usuario = usuarioRepository.findById(request.getIdUsuario())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El usuario indicado no existe"));
        }

        Instant now = Instant.now();

        Estudiante estudiante = new Estudiante();
        estudiante.setIdUsuario(usuario);
        estudiante.setIdTipoIdentificacion(tipo);
        estudiante.setNumeroIdentificacion(request.getNumeroIdentificacion());
        estudiante.setNombres(request.getNombres());
        estudiante.setPrimerApellido(request.getPrimerApellido());
        estudiante.setSegundoApellido(request.getSegundoApellido());
        estudiante.setFechaNacimiento(request.getFechaNacimiento());
        estudiante.setGenero(request.getGenero());
        estudiante.setTelefono(request.getTelefono());
        estudiante.setCorreo(request.getCorreo());
        estudiante.setDireccion(request.getDireccion());
        estudiante.setNivelEscolar(request.getNivelEscolar());
        estudiante.setIdCentroEducativo(request.getIdCentroEducativo());
        estudiante.setCondicionesRelevantes(request.getCondicionesRelevantes());
        estudiante.setFechaIngreso(request.getFechaIngreso() != null ? request.getFechaIngreso() : LocalDate.now());
        estudiante.setActivo(true);
        estudiante.setIdUsuarioUltimaModificacion(usuarioActual());
        estudiante.setCreadoEn(now);
        estudiante.setActualizadoEn(now);

        Estudiante guardado = estudianteRepository.save(estudiante);
        return toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<EstudianteResponse> listar(Boolean activo) {
        List<Estudiante> estudiantes = activo != null
                ? estudianteRepository.findByActivo(activo)
                : estudianteRepository.findAll();
        return estudiantes.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EstudianteResponse obtener(Long id) {
        return toResponse(buscarOFallar(id));
    }

    @Transactional
    public EstudianteResponse modificar(Long id, EstudianteUpdateRequest request) {
        Estudiante estudiante = buscarOFallar(id);

        TipoIdentificacion tipo = tipoIdentificacionRepository.findById(request.getIdTipoIdentificacion())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tipo de identificacion no existe"));

        boolean cambioIdentificacion =
                !request.getIdTipoIdentificacion().equals(estudiante.getIdTipoIdentificacion().getId())
                        || !request.getNumeroIdentificacion().equals(estudiante.getNumeroIdentificacion());

        if (cambioIdentificacion) {
            estudianteRepository.findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
                            request.getIdTipoIdentificacion(), request.getNumeroIdentificacion())
                    .ifPresent(e -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe otro estudiante con esa identificacion");
                    });
        }

        estudiante.setIdTipoIdentificacion(tipo);
        estudiante.setNumeroIdentificacion(request.getNumeroIdentificacion());
        estudiante.setNombres(request.getNombres());
        estudiante.setPrimerApellido(request.getPrimerApellido());
        estudiante.setSegundoApellido(request.getSegundoApellido());
        estudiante.setFechaNacimiento(request.getFechaNacimiento());
        estudiante.setGenero(request.getGenero());
        estudiante.setTelefono(request.getTelefono());
        estudiante.setCorreo(request.getCorreo());
        estudiante.setDireccion(request.getDireccion());
        estudiante.setNivelEscolar(request.getNivelEscolar());
        estudiante.setIdCentroEducativo(request.getIdCentroEducativo());
        estudiante.setCondicionesRelevantes(request.getCondicionesRelevantes());
        if (request.getFechaIngreso() != null) {
            estudiante.setFechaIngreso(request.getFechaIngreso());
        }
        estudiante.setFechaEgreso(request.getFechaEgreso());
        estudiante.setIdUsuarioUltimaModificacion(usuarioActual());
        estudiante.setActualizadoEn(Instant.now());

        return toResponse(estudianteRepository.save(estudiante));
    }

    // Baja logica: conserva el historial institucional (no se borra de la BD)
    @Transactional
    public EstudianteResponse darDeBaja(Long id) {
        Estudiante estudiante = buscarOFallar(id);
        estudiante.setActivo(false);
        estudiante.setFechaEgreso(LocalDate.now());
        estudiante.setIdUsuarioUltimaModificacion(usuarioActual());
        estudiante.setActualizadoEn(Instant.now());
        return toResponse(estudianteRepository.save(estudiante));
    }

    // Eliminacion fisica, pedida explicitamente en los criterios de aceptacion
    @Transactional
    public void eliminar(Long id) {
        Estudiante estudiante = buscarOFallar(id);
        estudianteRepository.delete(estudiante);
    }

    private Estudiante buscarOFallar(Long id) {
        return estudianteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado"));
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

    private EstudianteResponse toResponse(Estudiante e) {
        TipoIdentificacion tipo = e.getIdTipoIdentificacion();
        return new EstudianteResponse(
                e.getId(),
                new TipoIdentificacionResponse(tipo.getId(), tipo.getCodigo(), tipo.getNombre()),
                e.getNumeroIdentificacion(),
                e.getNombres(),
                e.getPrimerApellido(),
                e.getSegundoApellido(),
                e.getFechaNacimiento(),
                e.getGenero(),
                e.getTelefono(),
                e.getCorreo(),
                e.getDireccion(),
                e.getNivelEscolar(),
                e.getIdCentroEducativo(),
                e.getCondicionesRelevantes(),
                e.getFechaIngreso(),
                e.getFechaEgreso(),
                e.getActivo(),
                e.getCreadoEn(),
                e.getActualizadoEn()
        );
    }
}
