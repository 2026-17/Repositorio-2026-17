package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.HorarioGrupo;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.HorarioGrupoRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.response.AsignacionProfesorResponse;
import cr.acusab.una.sigac.presentation.dto.response.ProfesorAsignableResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class AsignacionProfesorService {

    private static final Set<String> ESTADOS_GRUPO_ACTIVOS =
            Set.of("PLANIFICADO", "PUBLICADO", "EN_CURSO");

    private final GrupoRepository grupoRepository;
    private final CursoRepository cursoRepository;
    private final ProfesorRepository profesorRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final HorarioGrupoRepository horarioRepository;
    private final UsuarioRepository usuarioRepository;

    public AsignacionProfesorService(
            GrupoRepository grupoRepository,
            CursoRepository cursoRepository,
            ProfesorRepository profesorRepository,
            PeriodoAcademicoRepository periodoRepository,
            HorarioGrupoRepository horarioRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.grupoRepository = grupoRepository;
        this.cursoRepository = cursoRepository;
        this.profesorRepository = profesorRepository;
        this.periodoRepository = periodoRepository;
        this.horarioRepository = horarioRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<ProfesorAsignableResponse> listarProfesoresActivos() {
        // Se listan todos los profesores: la asignación no se restringe por estado.
        return profesorRepository.findAllByOrderByNombresAscPrimerApellidoAsc()
                .stream()
                .map(p -> new ProfesorAsignableResponse(
                        p.getId(),
                        p.getNumeroIdentificacion(),
                        nombreProfesor(p),
                        p.getEspecialidad(),
                        Boolean.TRUE.equals(p.getActivo())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AsignacionProfesorResponse> listarAsignaciones() {
        return grupoRepository.findByEstadoInOrderByIdPeriodoAscCodigoAsc(
                        ESTADOS_GRUPO_ACTIVOS
                ).stream()
                .map(this::mapear)
                .toList();
    }

    @Transactional
    public AsignacionProfesorResponse asignar(Long idGrupo, Long idProfesor) {
        if (idGrupo == null || idGrupo <= 0 || idProfesor == null || idProfesor <= 0) {
            throw new BusinessException("El grupo y el profesor son obligatorios.");
        }

        // La asignación siempre se permite: no se valida estado del profesor,
        // del grupo, del curso o del período, ni fechas, horario o traslapes.
        Profesor profesor = profesorRepository.findById(idProfesor)
                .orElseThrow(() -> new BusinessException("El profesor indicado no existe."));

        Grupo grupo = grupoRepository.findByIdParaAsignacion(idGrupo)
                .orElseThrow(() -> new BusinessException("El grupo indicado no existe."));

        Long idUsuario = obtenerIdUsuarioAutenticado();
        grupo.setIdProfesor(profesor.getId());
        // La asignación habilita el grupo para la oferta académica.
        // Un grupo planificado pasa a publicado cuando ya tiene profesor.
        if ("PLANIFICADO".equals(grupo.getEstado())) {
            grupo.setEstado("PUBLICADO");
        }
        grupo.setIdUsuarioUltimaModificacion(idUsuario);
        grupo.setActualizadoEn(Instant.now());
        // El trigger registra en una sola entrada el cambio de profesor/estado.
        grupoRepository.save(grupo);

        return mapear(grupo);
    }

    @Transactional
    public void eliminarAsignacion(Long idGrupo) {
        Grupo grupo = grupoRepository.findByIdParaAsignacion(idGrupo)
                .orElseThrow(() -> new BusinessException("El grupo indicado no existe."));

        if (grupo.getIdProfesor() == null) {
            throw new BusinessException("El grupo no tiene un profesor asignado.");
        }

        Long idUsuario = obtenerIdUsuarioAutenticado();
        grupo.setIdProfesor(null);
        if ("PUBLICADO".equals(grupo.getEstado())) {
            grupo.setEstado("PLANIFICADO");
        }
        grupo.setIdUsuarioUltimaModificacion(idUsuario);
        grupo.setActualizadoEn(java.time.Instant.now());
        grupoRepository.save(grupo);
    }

    private AsignacionProfesorResponse mapear(Grupo grupo) {
        Curso curso = cursoRepository.findById(grupo.getIdCurso())
                .orElseThrow(() -> new BusinessException("No se encontró el curso del grupo."));
        PeriodoAcademico periodo = periodoRepository.findById(grupo.getIdPeriodo())
                .orElseThrow(() -> new BusinessException("No se encontró el período del grupo."));
        Profesor profesor = grupo.getIdProfesor() == null ? null
                : profesorRepository.findById(grupo.getIdProfesor()).orElse(null);
        List<AsignacionProfesorResponse.HorarioResponse> horarios =
                horarioRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(List.of(grupo.getId()))
                        .stream()
                        .sorted(Comparator.comparing(HorarioGrupo::getDiaSemana)
                                .thenComparing(HorarioGrupo::getHoraInicio))
                        .map(h -> new AsignacionProfesorResponse.HorarioResponse(
                                h.getId(), h.getDiaSemana(), h.getHoraInicio(), h.getHoraFin()
                        )).toList();
        return new AsignacionProfesorResponse(
                grupo.getId(), grupo.getCodigo(),
                curso.getId(), curso.getCodigo(), curso.getNombre(),
                periodo.getId(), periodo.getNombre(), periodo.getFechaInicio(),
                profesor == null ? null : profesor.getId(),
                profesor == null ? null : nombreProfesor(profesor),
                Boolean.TRUE.equals(curso.getActivo()),
                horarios
        );
    }

    private String nombreProfesor(Profesor p) {
        return String.join(" ", p.getNombres(), p.getPrimerApellido(),
                p.getSegundoApellido() == null ? "" : p.getSegundoApellido())
                .trim().replaceAll("\\s+", " ");
    }

    private Long obtenerIdUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalStateException("No se pudo identificar al usuario autenticado.");
        }
        Usuario usuario = usuarioRepository.findByCedula(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("El usuario autenticado no existe en el sistema."));
        return usuario.getId();
    }
}
