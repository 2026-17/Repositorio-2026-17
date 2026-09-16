package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.MallaCurricular;
import cr.acusab.una.sigac.data.entity.PlanEstudio;
import cr.acusab.una.sigac.data.entity.RequisitoCurso;
import cr.acusab.una.sigac.data.entity.TipoRequisitoCurso;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.MallaCurricularRepository;
import cr.acusab.una.sigac.data.repository.PlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.RequisitoCursoRepository;
import cr.acusab.una.sigac.data.repository.TipoRequisitoCursoRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.presentation.dto.request.RequisitosCursoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.RequisitosCursoResponseDTO;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RequisitoCursoService {
    private static final String PRE = "PRERREQUISITO";
    private static final String CO = "CORREQUISITO";

    private final CursoRepository cursoRepository;
    private final MallaCurricularRepository mallaRepository;
    private final PlanEstudioRepository planRepository;
    private final RequisitoCursoRepository requisitoRepository;
    private final TipoRequisitoCursoRepository tipoRepository;
    private final UsuarioRepository usuarioRepository;

    public RequisitoCursoService(CursoRepository cursoRepository,
                                 MallaCurricularRepository mallaRepository,
                                 PlanEstudioRepository planRepository,
                                 RequisitoCursoRepository requisitoRepository,
                                 TipoRequisitoCursoRepository tipoRepository,
                                 UsuarioRepository usuarioRepository) {
        this.cursoRepository = cursoRepository;
        this.mallaRepository = mallaRepository;
        this.planRepository = planRepository;
        this.requisitoRepository = requisitoRepository;
        this.tipoRepository = tipoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<RequisitosCursoResponseDTO.PlanDTO> listarPlanes() {
        return planRepository.findByVigenteTrueOrderByNombreAsc().stream()
                .map(p -> new RequisitosCursoResponseDTO.PlanDTO(p.getId(), p.getCodigo(), p.getNombre(), p.getVersion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RequisitosCursoResponseDTO.CursoRequisitoDTO> listarCursos(Long idPlan) {
        validarPlan(idPlan);
        return mallaRepository.findByIdPlanEstudioAndActivoTrueOrderByNivelAscOrdenEnNivelAsc(idPlan).stream()
                .map(m -> cursoRepository.findById(m.getIdCurso()).map(c ->
                        new RequisitosCursoResponseDTO.CursoRequisitoDTO(
                                c.getId(), c.getCodigo(), c.getNombre(), m.getNivel(), m.getOrdenEnNivel())).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public RequisitosCursoResponseDTO consultar(Long idPlan, Long idCurso) {
        PlanEstudio plan = validarPlan(idPlan);
        Curso curso = validarCursoEnPlan(idPlan, idCurso);

        Map<String, Short> tipos = tiposRequisito();
        RequisitosCursoResponseDTO dto = baseResponse(idPlan, curso);

        for (RequisitoCurso r : requisitoRepository.findByIdPlanEstudioAndIdCursoAndActivoTrue(idPlan, idCurso)) {
            if (Objects.equals(r.getIdTipoRequisito(), tipos.get(PRE))) dto.getPrerrequisitos().add(r.getIdCursoRequerido());
            if (Objects.equals(r.getIdTipoRequisito(), tipos.get(CO))) dto.getCorrequisitos().add(r.getIdCursoRequerido());
        }

        dto.setCursos(listarCursos(idPlan).stream().filter(c -> !Objects.equals(c.getIdCurso(), idCurso)).toList());
        return dto;
    }

    @Transactional
    public RequisitosCursoResponseDTO configurar(RequisitosCursoRequestDTO request) {
        Long idPlan = request.getIdPlanEstudio();
        Long idCurso = request.getIdCurso();
        validarPlan(idPlan);
        validarCursoEnPlan(idPlan, idCurso);

        List<Long> pres = validarLista(request.getPrerrequisitos(), idCurso, idPlan);
        List<Long> cos = validarLista(request.getCorrequisitos(), idCurso, idPlan);

        if (!Collections.disjoint(pres, cos)) {
            throw new IllegalArgumentException("Un curso no puede ser simultáneamente prerrequisito y correquisito.");
        }

        Map<String, Short> tipos = tiposRequisito();
        validarCiclos(idPlan, idCurso, pres, tipos.get(PRE));

        Long idUsuario = obtenerIdUsuarioAutenticado();
        Instant ahora = Instant.now();
        List<RequisitoCurso> actuales = requisitoRepository.findByIdPlanEstudioAndIdCursoAndActivoTrue(idPlan, idCurso);
        for (RequisitoCurso r : actuales) {
            r.setActivo(false);
            r.setIdUsuarioUltimaModificacion(idUsuario);
            r.setActualizadoEn(ahora);
        }
        requisitoRepository.saveAll(actuales);

        guardar(idPlan, idCurso, pres, tipos.get(PRE), idUsuario, ahora);
        guardar(idPlan, idCurso, cos, tipos.get(CO), idUsuario, ahora);

        return consultar(idPlan, idCurso);
    }

    private void guardar(Long plan, Long curso, List<Long> requeridos, Short tipo,
                         Long usuario, Instant ahora) {
        for (Long requerido : requeridos) {
            RequisitoCurso r = requisitoRepository
                    .findByIdPlanEstudioAndIdCursoAndIdCursoRequeridoAndIdTipoRequisito(plan, curso, requerido, tipo)
                    .orElseGet(RequisitoCurso::new);
            r.setIdPlanEstudio(plan);
            r.setIdCurso(curso);
            r.setIdCursoRequerido(requerido);
            r.setIdTipoRequisito(tipo);
            r.setActivo(true);
            r.setIdUsuarioUltimaModificacion(usuario);
            if (r.getCreadoEn() == null) r.setCreadoEn(ahora);
            r.setActualizadoEn(ahora);
            requisitoRepository.save(r);
        }
    }

    private void validarCiclos(Long plan, Long curso, List<Long> nuevos, Short tipoPre) {
        Map<Long, Set<Long>> grafo = new HashMap<>();
        for (RequisitoCurso r : requisitoRepository.findByIdPlanEstudioAndActivoTrue(plan)) {
            if (Objects.equals(r.getIdTipoRequisito(), tipoPre) && !Objects.equals(r.getIdCurso(), curso)) {
                grafo.computeIfAbsent(r.getIdCurso(), k -> new HashSet<>()).add(r.getIdCursoRequerido());
            }
        }
        grafo.put(curso, new HashSet<>(nuevos));
        Set<Long> visitados = new HashSet<>();
        Set<Long> recursion = new HashSet<>();
        for (Long n : grafo.keySet()) if (dfs(n, grafo, visitados, recursion)) {
            throw new IllegalArgumentException("La configuración de prerrequisitos generaría un ciclo entre cursos.");
        }
    }

    private boolean dfs(Long n, Map<Long, Set<Long>> grafo, Set<Long> visitados, Set<Long> recursion) {
        if (recursion.contains(n)) return true;
        if (!visitados.add(n)) return false;
        recursion.add(n);
        for (Long next : grafo.getOrDefault(n, Collections.emptySet())) if (dfs(next, grafo, visitados, recursion)) return true;
        recursion.remove(n);
        return false;
    }

    private List<Long> validarLista(List<Long> ids, Long curso, Long plan) {
        if (ids == null) return List.of();
        Set<Long> vistos = new HashSet<>();
        Set<Long> cursosPlan = mallaRepository.findByIdPlanEstudioAndActivoTrueOrderByNivelAscOrdenEnNivelAsc(plan)
                .stream().map(MallaCurricular::getIdCurso).collect(Collectors.toSet());
        for (Long id : ids) {
            if (id == null || id.equals(curso)) throw new IllegalArgumentException("Un curso no puede ser requisito de sí mismo.");
            if (!cursosPlan.contains(id)) throw new IllegalArgumentException("El curso " + id + " no pertenece al plan seleccionado.");
            if (!vistos.add(id)) throw new IllegalArgumentException("No se permiten cursos repetidos en una lista de requisitos.");
        }
        return List.copyOf(ids);
    }

    private PlanEstudio validarPlan(Long idPlan) {
        return planRepository.findById(idPlan).filter(p -> Boolean.TRUE.equals(p.getVigente()))
                .orElseThrow(() -> new IllegalArgumentException("El plan de estudio no existe o no está vigente."));
    }

    private Curso validarCursoEnPlan(Long plan, Long curso) {
        if (!cursoRepository.existsById(curso)) throw new IllegalArgumentException("El curso indicado no existe.");
        boolean pertenece = mallaRepository.findByIdPlanEstudioAndActivoTrueOrderByNivelAscOrdenEnNivelAsc(plan)
                .stream().anyMatch(m -> Objects.equals(m.getIdCurso(), curso));
        if (!pertenece) throw new IllegalArgumentException("El curso no pertenece al plan de estudio seleccionado.");
        return cursoRepository.findById(curso).orElseThrow();
    }

    private Map<String, Short> tiposRequisito() {
        return tipoRepository.findAll().stream().filter(t -> Boolean.TRUE.equals(t.getActivo()))
                .collect(Collectors.toMap(TipoRequisitoCurso::getCodigo, TipoRequisitoCurso::getId));
    }

    private RequisitosCursoResponseDTO baseResponse(Long plan, Curso curso) {
        RequisitosCursoResponseDTO dto = new RequisitosCursoResponseDTO();
        dto.setIdPlanEstudio(plan); dto.setIdCurso(curso.getId()); dto.setCodigoCurso(curso.getCodigo()); dto.setNombreCurso(curso.getNombre());
        return dto;
    }

    private Long obtenerIdUsuarioAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) throw new IllegalStateException("No se pudo identificar al usuario autenticado.");
        Usuario u = usuarioRepository.findByCedula(auth.getName()).orElseThrow(() -> new IllegalStateException("El usuario autenticado no existe en el sistema."));
        return u.getId();
    }
}
