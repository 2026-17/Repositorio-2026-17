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
import cr.acusab.una.sigac.presentation.dto.response.AsignacionProfesorResponse;
import cr.acusab.una.sigac.presentation.dto.response.ProfesorAsignableResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class AsignacionProfesorService {

    private static final Set<String> ESTADOS_GRUPO_ACTIVOS =
            Set.of("PLANIFICADO", "PUBLICADO", "EN_CURSO");

    private static final Set<String> ESTADOS_PERIODO_CERRADOS =
            Set.of("CERRADO", "CANCELADO");

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
        return profesorRepository.findByActivoTrueOrderByNombresAscPrimerApellidoAsc()
                .stream()
                .map(p -> new ProfesorAsignableResponse(
                        p.getId(),
                        p.getNumeroIdentificacion(),
                        nombreProfesor(p),
                        p.getEspecialidad()
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
            throw new IllegalArgumentException("El grupo y el profesor son obligatorios.");
        }

        Grupo grupo = grupoRepository.findByIdParaAsignacion(idGrupo)
                .orElseThrow(() -> new IllegalArgumentException("El grupo indicado no existe."));

        if ("CANCELADO".equals(grupo.getEstado()) || "CERRADO".equals(grupo.getEstado())) {
            throw new IllegalArgumentException("No se puede asignar un profesor a un grupo cerrado o cancelado.");
        }

        PeriodoAcademico periodo = periodoRepository.findById(grupo.getIdPeriodo())
                .orElseThrow(() -> new IllegalArgumentException("El período académico del grupo no existe."));

        if (ESTADOS_PERIODO_CERRADOS.contains(periodo.getEstado())) {
            throw new IllegalArgumentException("No se pueden modificar asignaciones de un período cerrado o cancelado.");
        }

        if (!LocalDate.now(ZoneId.of("America/Costa_Rica")).isBefore(periodo.getFechaInicio())) {
            throw new IllegalArgumentException("Las asignaciones solo se pueden modificar antes del inicio del período académico.");
        }

        Curso curso = cursoRepository.findById(grupo.getIdCurso())
                .orElseThrow(() -> new IllegalArgumentException("El curso asociado al grupo no existe."));
        if (!Boolean.TRUE.equals(curso.getActivo())) {
            throw new IllegalArgumentException("El curso debe estar activo para asignarle un profesor.");
        }

        Profesor profesor = profesorRepository.findByIdParaAsignacion(idProfesor)
                .orElseThrow(() -> new IllegalArgumentException("El profesor indicado no existe."));
        if (!Boolean.TRUE.equals(profesor.getActivo())) {
            throw new IllegalArgumentException("Solo se pueden asignar profesores activos y registrados.");
        }

        validarConflictos(grupo, idProfesor);

        Long idUsuario = obtenerIdUsuarioAutenticado();
        grupo.setIdProfesor(profesor.getId());
        // La asignación habilita el grupo para la oferta académica.
        // Un grupo planificado pasa a publicado cuando ya tiene profesor.
        if ("PLANIFICADO".equals(grupo.getEstado())) {
            grupo.setEstado("PUBLICADO");
        }
        grupo.setIdUsuarioUltimaModificacion(idUsuario);
        grupo.setActualizadoEn(java.time.Instant.now());
        // El trigger registra en una sola entrada el cambio de profesor/estado.
        grupoRepository.save(grupo);

        return mapear(grupo);
    }

    @Transactional
    public void eliminarAsignacion(Long idGrupo) {
        Grupo grupo = grupoRepository.findByIdParaAsignacion(idGrupo)
                .orElseThrow(() -> new IllegalArgumentException("El grupo indicado no existe."));

        if (grupo.getIdProfesor() == null) {
            throw new IllegalArgumentException("El grupo no tiene un profesor asignado.");
        }

        if ("CANCELADO".equals(grupo.getEstado()) || "CERRADO".equals(grupo.getEstado())) {
            throw new IllegalArgumentException("No se puede modificar una asignación de un grupo cerrado o cancelado.");
        }

        PeriodoAcademico periodo = periodoRepository.findById(grupo.getIdPeriodo())
                .orElseThrow(() -> new IllegalArgumentException("El período académico del grupo no existe."));

        if (ESTADOS_PERIODO_CERRADOS.contains(periodo.getEstado())) {
            throw new IllegalArgumentException("No se pueden modificar asignaciones de un período cerrado o cancelado.");
        }

        if (!LocalDate.now(ZoneId.of("America/Costa_Rica")).isBefore(periodo.getFechaInicio())) {
            throw new IllegalArgumentException("Las asignaciones solo se pueden eliminar antes del inicio del período académico.");
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

    private void validarConflictos(Grupo destino, Long idProfesor) {
        List<HorarioGrupo> horariosDestino =
                horarioRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(
                        List.of(destino.getId())
                );
        if (horariosDestino.isEmpty()) {
            throw new IllegalArgumentException("El grupo debe tener al menos un horario registrado antes de asignarle un profesor.");
        }

        List<Grupo> gruposProfesor = grupoRepository.findGruposProfesorEnPeriodo(
                idProfesor,
                destino.getIdPeriodo()
        );
        List<Long> ids = gruposProfesor.stream()
                .filter(g -> !g.getId().equals(destino.getId()))
                .map(Grupo::getId)
                .toList();
        if (ids.isEmpty()) return;

        List<HorarioGrupo> horariosOcupados =
                horarioRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(ids);
        for (HorarioGrupo nuevo : horariosDestino) {
            for (HorarioGrupo existente : horariosOcupados) {
                boolean mismoDia = nuevo.getDiaSemana().equals(existente.getDiaSemana());
                boolean traslape = nuevo.getHoraInicio().isBefore(existente.getHoraFin())
                        && existente.getHoraInicio().isBefore(nuevo.getHoraFin());
                if (mismoDia && traslape) {
                    Grupo grupoConflicto = gruposProfesor.stream()
                            .filter(g -> g.getId().equals(existente.getIdGrupo()))
                            .findFirst().orElse(null);
                    throw new IllegalArgumentException(
                            "Conflicto de horario: el profesor ya tiene un grupo asignado en ese horario"
                                    + (grupoConflicto != null ? " (grupo " + grupoConflicto.getCodigo() + ")." : ".")
                    );
                }
            }
        }
    }

    private AsignacionProfesorResponse mapear(Grupo grupo) {
        Curso curso = cursoRepository.findById(grupo.getIdCurso())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el curso del grupo."));
        PeriodoAcademico periodo = periodoRepository.findById(grupo.getIdPeriodo())
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el período del grupo."));
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
