package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.EstudiantePlanEstudio;
import cr.acusab.una.sigac.data.entity.PlanEstudio;
import cr.acusab.una.sigac.data.repository.EstudiantePlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.PlanEstudioRepository;
import cr.acusab.una.sigac.presentation.dto.response.PlanEstudioOpcionResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * Inscripción de estudiantes a planes de estudio.
 *
 * Un estudiante puede estar inscrito en varios planes y solo puede matricular
 * cursos de los planes en los que está inscrito (Historia 2). Al registrar un
 * estudiante se debe indicar al menos un plan.
 *
 * Quitar un plan no borra la inscripción: queda inactiva, para conservar el
 * historial.
 *
 * Los errores de validación son IllegalArgumentException: el manejador global
 * los devuelve como 400 con su mensaje, que es lo que debe ver quien registra
 * al estudiante.
 */
@Service
public class InscripcionPlanEstudioService {

    private final EstudiantePlanEstudioRepository inscripcionRepository;
    private final PlanEstudioRepository planRepository;

    public InscripcionPlanEstudioService(
            EstudiantePlanEstudioRepository inscripcionRepository,
            PlanEstudioRepository planRepository
    ) {
        this.inscripcionRepository = inscripcionRepository;
        this.planRepository = planRepository;
    }

    @Transactional(readOnly = true)
    public List<PlanEstudioOpcionResponse> listarPlanesVigentes() {

        return planRepository
                .findByVigenteTrueOrderByNombreAsc()
                .stream()
                .map(plan -> new PlanEstudioOpcionResponse(
                        plan.getId(),
                        plan.getCodigo(),
                        plan.getNombre(),
                        plan.getVersion()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> idsPlanesActivos(Long idEstudiante) {

        return inscripcionRepository
                .findByIdEstudianteAndActivoTrue(idEstudiante)
                .stream()
                .map(EstudiantePlanEstudio::getIdPlanEstudio)
                .sorted()
                .toList();
    }

    /*
     * Valida la selección antes de crear el usuario y el estudiante, para
     * rechazar el registro sin haber hecho ningún trabajo.
     */
    @Transactional(readOnly = true)
    public void validarParaRegistro(List<Long> idsSolicitados) {
        validar(idsSolicitados, Set.of());
    }

    /*
     * Deja al estudiante inscrito exactamente en los planes indicados:
     * inscribe los nuevos, reactiva los que estaban inactivos y desactiva
     * los que ya no se seleccionaron.
     *
     * Devuelve los ids de los planes en los que queda inscrito.
     */
    @Transactional
    public List<Long> sincronizar(Long idEstudiante, List<Long> idsSolicitados) {

        List<EstudiantePlanEstudio> existentes =
                inscripcionRepository.findByIdEstudiante(idEstudiante);

        Set<Long> yaActivos =
                existentes
                        .stream()
                        .filter(inscripcion -> Boolean.TRUE.equals(inscripcion.getActivo()))
                        .map(EstudiantePlanEstudio::getIdPlanEstudio)
                        .collect(Collectors.toSet());

        List<Long> ids = validar(idsSolicitados, yaActivos);

        Map<Long, EstudiantePlanEstudio> porPlan =
                existentes
                        .stream()
                        .collect(Collectors.toMap(
                                EstudiantePlanEstudio::getIdPlanEstudio,
                                inscripcion -> inscripcion
                        ));

        for (Long idPlan : ids) {

            EstudiantePlanEstudio inscripcion = porPlan.get(idPlan);

            if (inscripcion == null) {

                inscripcion = new EstudiantePlanEstudio();
                inscripcion.setIdEstudiante(idEstudiante);
                inscripcion.setIdPlanEstudio(idPlan);
                inscripcion.setActivo(true);
                inscripcionRepository.save(inscripcion);

            } else if (!Boolean.TRUE.equals(inscripcion.getActivo())) {

                inscripcion.setActivo(true);
                inscripcionRepository.save(inscripcion);
            }
        }

        for (EstudiantePlanEstudio inscripcion : existentes) {

            if (Boolean.TRUE.equals(inscripcion.getActivo())
                    && !ids.contains(inscripcion.getIdPlanEstudio())) {

                inscripcion.setActivo(false);
                inscripcionRepository.save(inscripcion);
            }
        }

        return ids.stream().sorted().toList();
    }

    /*
     * La selección es obligatoria, sin repetidos, y cada plan debe existir
     * y estar vigente. Un plan que dejó de estar vigente se acepta solo si
     * el estudiante ya estaba inscrito en él (para no obligar a quitarlo).
     */
    private List<Long> validar(List<Long> idsSolicitados, Set<Long> idsYaInscritos) {

        if (idsSolicitados == null || idsSolicitados.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un plan de estudio.");
        }

        if (idsSolicitados.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("La selección de planes de estudio no es válida.");
        }

        List<Long> ids = idsSolicitados.stream().distinct().toList();

        Map<Long, PlanEstudio> planes =
                planRepository
                        .findAllById(ids)
                        .stream()
                        .collect(Collectors.toMap(
                                PlanEstudio::getId,
                                plan -> plan
                        ));

        if (planes.size() != ids.size()) {
            throw new IllegalArgumentException("Uno o más de los planes de estudio seleccionados no existen.");
        }

        for (Long id : ids) {

            PlanEstudio plan = planes.get(id);

            if (!Boolean.TRUE.equals(plan.getVigente()) && !idsYaInscritos.contains(id)) {
                throw new IllegalArgumentException(
                        "El plan de estudio «" + plan.getNombre() + "» no se encuentra vigente."
                );
            }
        }

        return ids;
    }
}
