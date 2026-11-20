package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.EstudiantesInstrumento;
import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.repository.EstudianteInstrumentoRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.InstrumentoRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.request.InstrumentoEstudianteRequest;
import cr.acusab.una.sigac.presentation.dto.response.InstrumentoEstudianteResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/*
 * HU-026 - Registrar instrumentos que aprende el estudiante.
 *
 * Como administrador, quiero asignar uno o mas instrumentos (para
 * aprendizaje) a un estudiante para mantener registrado el
 * instrumento asociado a su expediente académico.
 *
 * Reglas clave de la HU:
 * - Un estudiante puede tener uno o más instrumentos vigentes
 *   al mismo tiempo. Cada uno avanza por separado (Instrumento I..XIV),
 *   con su profesor y su espacio de instrumento.
 * - Exactamente uno de los vigentes es el "instrumento del plan"
 *   (es_principal): el que cuenta para graduarse. Los demás son extras
 *   y solo avanzan en sus niveles. El primero que se asigna queda como
 *   del plan; después se puede cambiar cuál es.
 * - Cada asignación queda con su propia fecha de inicio.
 * - Un instrumento vigente se puede deshabilitar (finalizar):
 *   se le registra fecha_fin (la fecha en que se deshabilita) y
 *   se conserva en el historial, no se borra.
 */
@Service
public class InstrumentoEstudianteService {

    private final EstudianteRepository estudianteRepository;
    private final InstrumentoRepository instrumentoRepository;
    private final EstudianteInstrumentoRepository estudianteInstrumentoRepository;
    private final ProfesorRepository profesorRepository;

    public InstrumentoEstudianteService(
            EstudianteRepository estudianteRepository,
            InstrumentoRepository instrumentoRepository,
            EstudianteInstrumentoRepository estudianteInstrumentoRepository,
            ProfesorRepository profesorRepository
    ) {
        this.estudianteRepository = estudianteRepository;
        this.instrumentoRepository = instrumentoRepository;
        this.estudianteInstrumentoRepository = estudianteInstrumentoRepository;
        this.profesorRepository = profesorRepository;
    }

    /*
     * HU-026 - Asignar un instrumento al estudiante.
     *
     * El instrumento se agrega a la lista de instrumentos vigentes
     * del estudiante. La HU permite tener varios instrumentos
     * vigentes a la vez.
     */
    @Transactional
    public InstrumentoEstudianteResponse asignar(Long idEstudiante, InstrumentoEstudianteRequest request) {

        // 1. Validar que el estudiante exista (criterio de aceptación).
        Estudiante estudiante = obtenerEstudiante(idEstudiante);

        // 2. Validar que se haya seleccionado un instrumento y que
        // exista y esté activo (criterio de aceptación).
        Instrumento instrumento = obtenerInstrumentoActivo(request.getIdInstrumento());

        // 3. Evitar asignar dos veces el mismo instrumento mientras
        // ya está vigente (no tiene fecha_fin).
        estudianteInstrumentoRepository
                .findByIdEstudiante_IdAndIdInstrumento_IdAndFechaFinIsNull(idEstudiante, instrumento.getId())
                .ifPresent(asignacion -> {
                    throw new BusinessException(
                            "El estudiante ya tiene asignado ese instrumento actualmente.");
                });

        // 3.1 Si se indicó un profesor, validar que exista, esté activo
        // y tenga ese instrumento como especialidad.
        Profesor profesor = obtenerProfesorParaInstrumento(request.getIdProfesor(), instrumento);

        // 4. Instrumento del plan: el primero vigente queda como del plan.
        // Si se pide que este sea el del plan, los demás pasan a extras.
        List<EstudiantesInstrumento> vigentes =
                estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(idEstudiante);

        boolean delPlan = vigentes.isEmpty() || Boolean.TRUE.equals(request.getEsPrincipal());

        if (delPlan) {
            vigentes.stream()
                    .filter(v -> Boolean.TRUE.equals(v.getEsPrincipal()))
                    .forEach(v -> {
                        v.setEsPrincipal(false);
                        estudianteInstrumentoRepository.save(v);
                    });
        }

        // 5. Registrar la nueva asignación con su fecha de inicio.
        EstudiantesInstrumento asignacion = crearAsignacion(
                estudiante, instrumento, profesor, request.getObservaciones());
        asignacion.setEsPrincipal(delPlan);

        return mapearAResponse(estudianteInstrumentoRepository.save(asignacion));
    }

    /*
     * Cambiar cuál instrumento vigente es el del plan. El anterior pasa a
     * ser extra.
     */
    @Transactional
    public InstrumentoEstudianteResponse marcarDelPlan(Long idEstudiante, Long idEstudianteInstrumento) {
        obtenerEstudiante(idEstudiante);

        EstudiantesInstrumento elegido = obtenerAsignacionDelEstudiante(idEstudiante, idEstudianteInstrumento);

        if (elegido.getFechaFin() != null) {
            throw new BusinessException("Solo un instrumento vigente puede ser el instrumento del plan.");
        }

        for (EstudiantesInstrumento vigente :
                estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(idEstudiante)) {
            boolean esElElegido = vigente.getId().equals(elegido.getId());
            if (!esElElegido && Boolean.TRUE.equals(vigente.getEsPrincipal())) {
                vigente.setEsPrincipal(false);
                estudianteInstrumentoRepository.save(vigente);
            }
        }

        elegido.setEsPrincipal(true);
        return mapearAResponse(estudianteInstrumentoRepository.save(elegido));
    }

    /*
     * HU-026 - Deshabilitar (finalizar) una asignación de instrumento
     * vigente del estudiante.
     *
     * Registra fecha_fin = hoy y conserva el registro en el
     * historial; no se elimina.
     */
    @Transactional
    public InstrumentoEstudianteResponse deshabilitar(Long idEstudiante, Long idEstudianteInstrumento) {
        obtenerEstudiante(idEstudiante);

        EstudiantesInstrumento asignacion = obtenerAsignacionDelEstudiante(idEstudiante, idEstudianteInstrumento);

        if (asignacion.getFechaFin() != null) {
            throw new BusinessException("Ese instrumento ya se encuentra finalizado.");
        }

        boolean eraDelPlan = Boolean.TRUE.equals(asignacion.getEsPrincipal());

        asignacion.setFechaFin(LocalDate.now());
        asignacion.setEsPrincipal(false);
        EstudiantesInstrumento guardada = estudianteInstrumentoRepository.save(asignacion);

        // Si se finalizó el del plan, el vigente más antiguo pasa a serlo.
        if (eraDelPlan) {
            estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(idEstudiante)
                    .stream()
                    .filter(v -> !v.getId().equals(guardada.getId()))
                    .min(Comparator.comparing(EstudiantesInstrumento::getFechaInicio)
                            .thenComparing(EstudiantesInstrumento::getId))
                    .ifPresent(siguiente -> {
                        siguiente.setEsPrincipal(true);
                        estudianteInstrumentoRepository.save(siguiente);
                    });
        }

        return mapearAResponse(guardada);
    }

    private EstudiantesInstrumento obtenerAsignacionDelEstudiante(Long idEstudiante, Long idEstudianteInstrumento) {
        EstudiantesInstrumento asignacion = estudianteInstrumentoRepository
                .findById(idEstudianteInstrumento)
                .orElseThrow(() -> new BusinessException("La asignación de instrumento indicada no existe."));

        if (!asignacion.getEstudiante().getId().equals(idEstudiante)) {
            throw new BusinessException("La asignación indicada no pertenece a este estudiante.");
        }
        return asignacion;
    }

    /*
     * HU-026 - Consultar el historial completo de instrumentos
     * del estudiante (vigentes y ya cerrados), del más reciente
     * al más antiguo.
     */
    @Transactional(readOnly = true)
    public List<InstrumentoEstudianteResponse> listarHistorial(Long idEstudiante) {
        obtenerEstudiante(idEstudiante);

        return estudianteInstrumentoRepository
                .findByIdEstudiante_IdOrderByFechaInicioDesc(idEstudiante)
                .stream()
                .map(this::mapearAResponse)
                .toList();
    }

    /*
     * HU-026 - Consultar el o los instrumentos que el estudiante
     * tiene asignados actualmente (sin fecha_fin).
     */
    @Transactional(readOnly = true)
    public List<InstrumentoEstudianteResponse> listarVigentes(Long idEstudiante) {
        obtenerEstudiante(idEstudiante);

        return estudianteInstrumentoRepository
                .findByIdEstudiante_IdAndFechaFinIsNull(idEstudiante)
                .stream()
                .map(this::mapearAResponse)
                .toList();
    }

    // --- Métodos de apoyo ---

    private EstudiantesInstrumento crearAsignacion(
            Estudiante estudiante, Instrumento instrumento, Profesor profesor, String observaciones) {

        EstudiantesInstrumento asignacion = new EstudiantesInstrumento();
        asignacion.setEstudiante(estudiante);
        asignacion.setInstrumento(instrumento);
        asignacion.setProfesor(profesor);
        asignacion.setFechaInicio(LocalDate.now());
        asignacion.setObservaciones(observaciones);
        return asignacion;
    }

    // HU-026 - El profesor es opcional, pero si se indica debe existir,
    // estar activo y tener ese instrumento entre sus especialidades
    // (campo de texto separado por comas).
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
        boolean tieneEspecialidad = especialidad != null &&
                especialidad.toLowerCase().contains(instrumento.getNombre().toLowerCase());

        if (!tieneEspecialidad) {
            throw new BusinessException(
                    "El profesor seleccionado no tiene " + instrumento.getNombre() + " como especialidad.");
        }

        return profesor;
    }

    private Estudiante obtenerEstudiante(Long idEstudiante) {
        if (idEstudiante == null) {
            throw new BusinessException("El estudiante es obligatorio.");
        }
        return estudianteRepository.findById(idEstudiante)
                .orElseThrow(() -> new BusinessException("El estudiante indicado no existe."));
    }

    private Instrumento obtenerInstrumentoActivo(Integer idInstrumento) {
        if (idInstrumento == null) {
            throw new BusinessException("Debe seleccionar un instrumento.");
        }
        return instrumentoRepository.findByIdAndActivoTrue(idInstrumento)
                .orElseThrow(() -> new BusinessException(
                        "El instrumento indicado no existe o está inactivo."));
    }

    private InstrumentoEstudianteResponse mapearAResponse(EstudiantesInstrumento asignacion) {
        Instrumento instrumento = asignacion.getInstrumento();
        Profesor profesor = asignacion.getProfesor();

        return new InstrumentoEstudianteResponse(
                asignacion.getId(),
                asignacion.getEstudiante().getId(),
                instrumento.getId(),
                instrumento.getCodigo(),
                instrumento.getNombre(),
                profesor != null ? profesor.getId() : null,
                profesor != null ? nombreCompleto(profesor) : null,
                asignacion.getFechaInicio(),
                asignacion.getFechaFin(),
                asignacion.getFechaFin() == null,
                asignacion.getObservaciones(),
                Boolean.TRUE.equals(asignacion.getEsPrincipal())
        );
    }

    private String nombreCompleto(Profesor p) {
        StringBuilder sb = new StringBuilder(p.getNombres()).append(" ").append(p.getPrimerApellido());
        if (p.getSegundoApellido() != null && !p.getSegundoApellido().isBlank()) {
            sb.append(" ").append(p.getSegundoApellido());
        }
        return sb.toString();
    }
}
