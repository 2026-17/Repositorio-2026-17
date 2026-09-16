package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.MallaCurricular;
import cr.acusab.una.sigac.data.entity.PlanEstudio;
import cr.acusab.una.sigac.data.entity.RequisitosCurso;
import cr.acusab.una.sigac.data.entity.TiposRequisitosCurso;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.MallaCurricularRepository;
import cr.acusab.una.sigac.data.repository.PlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.RequisitosCursoRepository;
import cr.acusab.una.sigac.data.repository.TiposRequisitosCursoRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.presentation.dto.request.RequisitosCursoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.RequisitosCursoResponseDTO;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RequisitosCursoService {

    private static final String PRERREQUISITO = "PRERREQUISITO";
    private static final String CORREQUISITO = "CORREQUISITO";

    private final RequisitosCursoRepository requisitosRepository;
    private final TiposRequisitosCursoRepository tiposRepository;
    private final CursoRepository cursoRepository;
    private final MallaCurricularRepository mallaRepository;
    private final PlanEstudioRepository planRepository;
    private final UsuarioRepository usuarioRepository;

    public RequisitosCursoService(
            RequisitosCursoRepository requisitosRepository,
            TiposRequisitosCursoRepository tiposRepository,
            CursoRepository cursoRepository,
            MallaCurricularRepository mallaRepository,
            PlanEstudioRepository planRepository,
            UsuarioRepository usuarioRepository) {
        this.requisitosRepository = requisitosRepository;
        this.tiposRepository = tiposRepository;
        this.cursoRepository = cursoRepository;
        this.mallaRepository = mallaRepository;
        this.planRepository = planRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public RequisitosCursoResponseDTO consultar(Long idPlanEstudio, Long idCurso) {
        PlanEstudio plan = obtenerPlan(idPlanEstudio);
        Curso curso = obtenerCurso(idCurso);
        validarCursoEnPlan(plan.getId(), curso.getId());

        RequisitosCursoResponseDTO response = construirRespuestaBase(plan, curso);
        response.setCursos(mallaRepository.findCursosActivosDePlan(plan.getId()).stream()
                .map(m -> new RequisitosCursoResponseDTO.CursoRequisitoDTO(
                        m.getIdCurso().getId(),
                        m.getIdCurso().getCodigo(),
                        m.getIdCurso().getNombre(),
                        m.getNivel(),
                        m.getOrdenEnNivel()))
                .filter(c -> !c.getIdCurso().equals(curso.getId()))
                .toList());

        for (RequisitosCurso requisito : requisitosRepository.findConfiguracionActiva(plan.getId(), curso.getId())) {
            if (PRERREQUISITO.equals(requisito.getIdTipoRequisito().getCodigo())) {
                response.getPrerrequisitos().add(requisito.getIdCursoRequerido().getId());
            } else if (CORREQUISITO.equals(requisito.getIdTipoRequisito().getCodigo())) {
                response.getCorrequisitos().add(requisito.getIdCursoRequerido().getId());
            }
        }

        return response;
    }

    @Transactional
    public RequisitosCursoResponseDTO guardar(RequisitosCursoRequestDTO request) {
        PlanEstudio plan = obtenerPlan(request.getIdPlanEstudio());
        Curso curso = obtenerCurso(request.getIdCurso());
        validarCursoEnPlan(plan.getId(), curso.getId());

        Set<Long> prerrequisitos = normalizar(request.getPrerrequisitos());
        Set<Long> correquisitos = normalizar(request.getCorrequisitos());

        if (prerrequisitos.contains(curso.getId()) || correquisitos.contains(curso.getId())) {
            throw new IllegalArgumentException("Un curso no puede ser prerrequisito o correquisito de sí mismo.");
        }

        Set<Long> cursosDelPlan = mallaRepository.findCursosActivosDePlan(plan.getId()).stream()
                .map(m -> m.getIdCurso().getId())
                .collect(Collectors.toSet());

        validarCursosDelPlan(prerrequisitos, cursosDelPlan);
        validarCursosDelPlan(correquisitos, cursosDelPlan);
        validarCiclos(plan.getId(), curso.getId(), prerrequisitos);

        TiposRequisitosCurso tipoPre = obtenerTipo(PRERREQUISITO);
        TiposRequisitosCurso tipoCo = obtenerTipo(CORREQUISITO);
        Usuario usuario = obtenerUsuarioAutenticado();
        Instant ahora = Instant.now();

        List<RequisitosCurso> actuales = requisitosRepository.findConfiguracionActiva(plan.getId(), curso.getId());
        Map<String, RequisitosCurso> actualesPorClave = new HashMap<>();
        for (RequisitosCurso actual : actuales) {
            actualesPorClave.put(clave(actual.getIdCursoRequerido().getId(), actual.getIdTipoRequisito().getCodigo()), actual);
        }

        Set<String> deseados = new HashSet<>();
        for (Long idRequerido : prerrequisitos) {
            deseados.add(clave(idRequerido, PRERREQUISITO));
        }
        for (Long idRequerido : correquisitos) {
            deseados.add(clave(idRequerido, CORREQUISITO));
        }

        // Eliminar únicamente relaciones que ya no fueron seleccionadas.
        List<RequisitosCurso> eliminar = actuales.stream()
                .filter(r -> !deseados.contains(clave(
                        r.getIdCursoRequerido().getId(),
                        r.getIdTipoRequisito().getCodigo())))
                .toList();
        if (!eliminar.isEmpty()) {
            requisitosRepository.deleteAll(eliminar);
        }

        // Crear únicamente relaciones nuevas. Las que permanecen no generan
        // cambios innecesarios en la bitácora.
        for (Long idRequerido : prerrequisitos) {
            String clave = clave(idRequerido, PRERREQUISITO);
            if (!actualesPorClave.containsKey(clave)) {
                requisitosRepository.save(crearRelacion(plan, curso, idRequerido, tipoPre, usuario, ahora));
            }
        }
        for (Long idRequerido : correquisitos) {
            String clave = clave(idRequerido, CORREQUISITO);
            if (!actualesPorClave.containsKey(clave)) {
                requisitosRepository.save(crearRelacion(plan, curso, idRequerido, tipoCo, usuario, ahora));
            }
        }

        return consultar(plan.getId(), curso.getId());
    }

    private RequisitosCurso crearRelacion(
            PlanEstudio plan,
            Curso curso,
            Long idCursoRequerido,
            TiposRequisitosCurso tipo,
            Usuario usuario,
            Instant ahora) {
        Curso requerido = obtenerCurso(idCursoRequerido);

        RequisitosCurso relacion = new RequisitosCurso();
        relacion.setIdPlanEstudio(plan);
        relacion.setIdCurso(curso);
        relacion.setIdCursoRequerido(requerido);
        relacion.setIdTipoRequisito(tipo);
        relacion.setNotaMinima(null);
        relacion.setActivo(true);
        relacion.setIdUsuarioUltimaModificacion(usuario);
        relacion.setCreadoEn(ahora);
        relacion.setActualizadoEn(ahora);
        return relacion;
    }

    private void validarCiclos(Long idPlanEstudio, Long idCursoConfigurado, Set<Long> nuevosPrerrequisitos) {
        Map<Long, Set<Long>> grafo = new HashMap<>();

        for (RequisitosCurso r : requisitosRepository.findActivosPorPlanYTipo(idPlanEstudio, PRERREQUISITO)) {
            // Las relaciones actuales del curso que se está configurando
            // serán reemplazadas por la nueva selección.
            if (!r.getIdCurso().getId().equals(idCursoConfigurado)) {
                grafo.computeIfAbsent(r.getIdCurso().getId(), k -> new HashSet<>())
                        .add(r.getIdCursoRequerido().getId());
            }
        }

        grafo.computeIfAbsent(idCursoConfigurado, k -> new HashSet<>()).addAll(nuevosPrerrequisitos);

        Set<Long> visitados = new HashSet<>();
        Set<Long> enRuta = new HashSet<>();
        for (Long nodo : grafo.keySet()) {
            if (hayCiclo(nodo, grafo, visitados, enRuta)) {
                throw new IllegalArgumentException(
                        "La configuración de prerrequisitos genera un ciclo entre cursos.");
            }
        }
    }

    private boolean hayCiclo(
            Long nodo,
            Map<Long, Set<Long>> grafo,
            Set<Long> visitados,
            Set<Long> enRuta) {
        if (enRuta.contains(nodo)) {
            return true;
        }
        if (visitados.contains(nodo)) {
            return false;
        }

        visitados.add(nodo);
        enRuta.add(nodo);

        for (Long siguiente : grafo.getOrDefault(nodo, Set.of())) {
            if (hayCiclo(siguiente, grafo, visitados, enRuta)) {
                return true;
            }
        }

        enRuta.remove(nodo);
        return false;
    }

    private void validarCursosDelPlan(Set<Long> seleccionados, Set<Long> cursosDelPlan) {
        if (!cursosDelPlan.containsAll(seleccionados)) {
            throw new IllegalArgumentException(
                    "Todos los cursos seleccionados deben pertenecer al plan de estudio indicado.");
        }
    }

    private void validarCursoEnPlan(Long idPlan, Long idCurso) {
        boolean pertenece = mallaRepository.findCursosActivosDePlan(idPlan).stream()
                .anyMatch(m -> m.getIdCurso().getId().equals(idCurso));
        if (!pertenece) {
            throw new IllegalArgumentException("El curso no pertenece al plan de estudio indicado.");
        }
    }

    private PlanEstudio obtenerPlan(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El plan de estudio indicado no existe."));
    }

    private Curso obtenerCurso(Long id) {
        return cursoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El curso indicado no existe."));
    }

    private TiposRequisitosCurso obtenerTipo(String codigo) {
        return tiposRepository.findByCodigo(codigo)
                .orElseThrow(() -> new IllegalStateException("No existe el tipo de requisito " + codigo + "."));
    }

    private Usuario obtenerUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new IllegalStateException("No se pudo identificar al usuario autenticado.");
        }
        return usuarioRepository.findByCedula(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("El usuario autenticado no existe en el sistema."));
    }

    private Set<Long> normalizar(List<Long> ids) {
        return ids == null ? new HashSet<>() : ids.stream()
                .filter(id -> id != null)
                .collect(Collectors.toSet());
    }

    private String clave(Long idCurso, String tipo) {
        return idCurso + ":" + tipo;
    }

    private RequisitosCursoResponseDTO construirRespuestaBase(PlanEstudio plan, Curso curso) {
        RequisitosCursoResponseDTO response = new RequisitosCursoResponseDTO();
        response.setIdPlanEstudio(plan.getId());
        response.setIdCurso(curso.getId());
        response.setCodigoCurso(curso.getCodigo());
        response.setNombreCurso(curso.getNombre());
        return response;
    }
}
