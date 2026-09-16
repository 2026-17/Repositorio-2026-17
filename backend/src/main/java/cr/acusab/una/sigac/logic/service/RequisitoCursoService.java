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

@Service
public class RequisitoCursoService {

    private static final String PRERREQUISITO = "PRERREQUISITO";
    private static final String CORREQUISITO = "CORREQUISITO";

    private final CursoRepository cursoRepository;
    private final MallaCurricularRepository mallaCurricularRepository;
    private final PlanEstudioRepository planEstudioRepository;
    private final RequisitoCursoRepository requisitoCursoRepository;
    private final TipoRequisitoCursoRepository tipoRequisitoCursoRepository;
    private final UsuarioRepository usuarioRepository;

    public RequisitoCursoService(
            CursoRepository cursoRepository,
            MallaCurricularRepository mallaCurricularRepository,
            PlanEstudioRepository planEstudioRepository,
            RequisitoCursoRepository requisitoCursoRepository,
            TipoRequisitoCursoRepository tipoRequisitoCursoRepository,
            UsuarioRepository usuarioRepository) {
        this.cursoRepository = cursoRepository;
        this.mallaCurricularRepository = mallaCurricularRepository;
        this.planEstudioRepository = planEstudioRepository;
        this.requisitoCursoRepository = requisitoCursoRepository;
        this.tipoRequisitoCursoRepository = tipoRequisitoCursoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public RequisitosCursoResponseDTO configurar(RequisitosCursoRequestDTO request) {
        Long idCurso = request.getIdCurso();
        List<Long> prerrequisitos = normalizar(request.getPrerrequisitos());
        List<Long> correquisitos = normalizar(request.getCorrequisitos());

        if (prerrequisitos.contains(idCurso) || correquisitos.contains(idCurso)) {
            throw new IllegalArgumentException(
                    "Un curso no puede ser prerrequisito o correquisito de sí mismo."
            );
        }

        Set<Long> duplicados = new HashSet<>(prerrequisitos);
        if (duplicados.size() != prerrequisitos.size()) {
            throw new IllegalArgumentException("La lista de prerrequisitos contiene cursos repetidos.");
        }
        duplicados = new HashSet<>(correquisitos);
        if (duplicados.size() != correquisitos.size()) {
            throw new IllegalArgumentException("La lista de correquisitos contiene cursos repetidos.");
        }

        if (!Collections.disjoint(prerrequisitos, correquisitos)) {
            throw new IllegalArgumentException(
                    "Un mismo curso no puede configurarse simultáneamente como prerrequisito y correquisito."
            );
        }

        Curso curso = cursoRepository.findById(idCurso)
                .orElseThrow(() -> new IllegalArgumentException("El curso indicado no existe."));

        List<MallaCurricular> mallasCurso = mallaCurricularRepository
                .findByIdCursoAndActivoTrue(idCurso);

        if (mallasCurso.isEmpty()) {
            throw new IllegalArgumentException(
                    "El curso no pertenece a una malla curricular activa."
            );
        }

        Long idPlanEstudio = mallasCurso.get(0).getIdPlanEstudio();
        PlanEstudio plan = planEstudioRepository.findById(idPlanEstudio)
                .orElseThrow(() -> new IllegalArgumentException("El plan de estudio no existe."));

        if (!Boolean.TRUE.equals(plan.getVigente())) {
            throw new IllegalArgumentException("El plan de estudio del curso no está vigente.");
        }

        Set<Long> cursosDelPlan = new HashSet<>();
        for (MallaCurricular malla : mallaCurricularRepository
                .findByIdPlanEstudioAndActivoTrueOrderByNivelAscOrdenEnNivelAsc(idPlanEstudio)) {
            cursosDelPlan.add(malla.getIdCurso());
        }

        validarCursosDelPlan(prerrequisitos, cursosDelPlan);
        validarCursosDelPlan(correquisitos, cursosDelPlan);

        // Solo los prerrequisitos participan en la detección de ciclos.
        validarCiclos(idPlanEstudio, idCurso, prerrequisitos);

        Long idUsuario = obtenerIdUsuarioAutenticado();
        Instant ahora = Instant.now();

        Short idTipoPre = tipoRequisitoCursoRepository.findByCodigoAndActivoTrue(PRERREQUISITO)
                .map(TipoRequisitoCurso::getId)
                .orElseThrow(() -> new IllegalStateException("No existe el tipo PRERREQUISITO."));

        Short idTipoCo = tipoRequisitoCursoRepository.findByCodigoAndActivoTrue(CORREQUISITO)
                .map(TipoRequisitoCurso::getId)
                .orElseThrow(() -> new IllegalStateException("No existe el tipo CORREQUISITO."));

        List<RequisitoCurso> actuales = requisitoCursoRepository
                .findByIdPlanEstudioAndIdCursoAndActivoTrue(idPlanEstudio, idCurso);

        // Desactivación lógica para conservar el historial y activar la nueva configuración.
        for (RequisitoCurso actual : actuales) {
            actual.setActivo(false);
            actual.setIdUsuarioUltimaModificacion(idUsuario);
            actual.setActualizadoEn(ahora);
        }
        requisitoCursoRepository.saveAll(actuales);

        guardarRelaciones(idPlanEstudio, idCurso, prerrequisitos, idTipoPre, idUsuario, ahora);
        guardarRelaciones(idPlanEstudio, idCurso, correquisitos, idTipoCo, idUsuario, ahora);

        return new RequisitosCursoResponseDTO(
                curso.getId(),
                idPlanEstudio,
                prerrequisitos,
                correquisitos,
                "Prerrequisitos y correquisitos configurados correctamente."
        );
    }

    @Transactional(readOnly = true)
    public RequisitosCursoResponseDTO consultar(Long idCurso) {
        Curso curso = cursoRepository.findById(idCurso)
                .orElseThrow(() -> new IllegalArgumentException("El curso indicado no existe."));

        Long idPlan = mallaCurricularRepository.findByIdCursoAndActivoTrue(idCurso).stream()
                .findFirst()
                .map(MallaCurricular::getIdPlanEstudio)
                .orElseThrow(() -> new IllegalArgumentException("El curso no pertenece a una malla activa."));

        Short pre = tipoRequisitoCursoRepository.findByCodigoAndActivoTrue(PRERREQUISITO)
                .map(TipoRequisitoCurso::getId).orElseThrow();
        Short co = tipoRequisitoCursoRepository.findByCodigoAndActivoTrue(CORREQUISITO)
                .map(TipoRequisitoCurso::getId).orElseThrow();

        List<RequisitoCurso> relaciones = requisitoCursoRepository
                .findByIdPlanEstudioAndIdCursoAndActivoTrue(idPlan, idCurso);

        List<Long> prerrequisitos = relaciones.stream()
                .filter(r -> Objects.equals(r.getIdTipoRequisito(), pre))
                .map(RequisitoCurso::getIdCursoRequerido).toList();
        List<Long> correquisitos = relaciones.stream()
                .filter(r -> Objects.equals(r.getIdTipoRequisito(), co))
                .map(RequisitoCurso::getIdCursoRequerido).toList();

        return new RequisitosCursoResponseDTO(
                curso.getId(), idPlan, prerrequisitos, correquisitos,
                "Configuración consultada correctamente."
        );
    }

    private void guardarRelaciones(Long idPlan, Long idCurso, List<Long> requeridos,
                                   Short idTipo, Long idUsuario, Instant ahora) {
        for (Long requerido : requeridos) {
            Optional<RequisitoCurso> existente = requisitoCursoRepository
                    .findByIdPlanEstudioAndIdCursoAndIdCursoRequeridoAndIdTipoRequisito(
                            idPlan, idCurso, requerido, idTipo);

            RequisitoCurso relacion = existente.orElseGet(RequisitoCurso::new);
            relacion.setIdPlanEstudio(idPlan);
            relacion.setIdCurso(idCurso);
            relacion.setIdCursoRequerido(requerido);
            relacion.setIdTipoRequisito(idTipo);
            relacion.setActivo(true);
            relacion.setIdUsuarioUltimaModificacion(idUsuario);
            if (relacion.getCreadoEn() == null) relacion.setCreadoEn(ahora);
            relacion.setActualizadoEn(ahora);
            requisitoCursoRepository.save(relacion);
        }
    }

    private void validarCursosDelPlan(List<Long> cursos, Set<Long> cursosDelPlan) {
        for (Long id : cursos) {
            if (!cursosDelPlan.contains(id)) {
                throw new IllegalArgumentException(
                        "El curso " + id + " no pertenece a la malla curricular activa del plan."
                );
            }
        }
    }

    private void validarCiclos(Long idPlan, Long idCurso, List<Long> nuevosPrerrequisitos) {
        Map<Long, Set<Long>> grafo = new HashMap<>();

        for (RequisitoCurso r : requisitoCursoRepository.findByIdPlanEstudioAndActivoTrue(idPlan)) {
            if (Objects.equals(r.getIdCurso(), idCurso)) continue;
            Short tipoPre = tipoRequisitoCursoRepository.findByCodigoAndActivoTrue(PRERREQUISITO)
                    .map(TipoRequisitoCurso::getId).orElseThrow();
            if (Objects.equals(r.getIdTipoRequisito(), tipoPre)) {
                grafo.computeIfAbsent(r.getIdCurso(), k -> new HashSet<>()).add(r.getIdCursoRequerido());
            }
        }

        grafo.put(idCurso, new HashSet<>(nuevosPrerrequisitos));

        if (contieneCiclo(grafo)) {
            throw new IllegalArgumentException(
                    "La configuración de prerrequisitos generaría un ciclo entre cursos."
            );
        }
    }

    private boolean contieneCiclo(Map<Long, Set<Long>> grafo) {
        Set<Long> visitados = new HashSet<>();
        Set<Long> enRecursion = new HashSet<>();
        for (Long nodo : grafo.keySet()) {
            if (dfsCiclo(nodo, grafo, visitados, enRecursion)) return true;
        }
        return false;
    }

    private boolean dfsCiclo(Long nodo, Map<Long, Set<Long>> grafo,
                             Set<Long> visitados, Set<Long> enRecursion) {
        if (enRecursion.contains(nodo)) return true;
        if (!visitados.add(nodo)) return false;
        enRecursion.add(nodo);
        for (Long siguiente : grafo.getOrDefault(nodo, Collections.emptySet())) {
            if (dfsCiclo(siguiente, grafo, visitados, enRecursion)) return true;
        }
        enRecursion.remove(nodo);
        return false;
    }

    private List<Long> normalizar(List<Long> valores) {
        return valores == null ? List.of() : valores.stream().filter(Objects::nonNull).distinct().toList();
    }

    private Long obtenerIdUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new IllegalStateException("No se pudo identificar al usuario autenticado.");
        }
        String cedula = authentication.getName();
        Usuario usuario = usuarioRepository.findByCedula(cedula)
                .orElseThrow(() -> new IllegalStateException("El usuario autenticado no existe en el sistema."));
        return usuario.getId();
    }
}
