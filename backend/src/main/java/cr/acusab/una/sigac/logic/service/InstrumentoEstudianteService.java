package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.EstudiantesInstrumento;
import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.repository.EstudianteInstrumentoRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.InstrumentoRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.request.InstrumentoEstudianteRequest;
import cr.acusab.una.sigac.presentation.dto.response.InstrumentoEstudianteResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/*
 * HU-026 - Registrar instrumentos que aprende el estudiante.
 *
 * Como administrador, quiero asignar un instrumento (para
 * aprendizaje) a un estudiante para mantener registrado el
 * instrumento asociado a su expediente académico.
 *
 * Reglas clave de la HU:
 * - Un estudiante puede tener uno o más instrumentos asignados.
 * - Solo puede haber un instrumento "principal" vigente a la vez.
 * - Si el estudiante cambia de instrumento principal, la
 *   asignación anterior se cierra (fecha_fin) pero NO se borra:
 *   así se conserva el historial completo.
 * - Cada asignación queda con su propia fecha de inicio.
 */
@Service
public class InstrumentoEstudianteService {

    private final EstudianteRepository estudianteRepository;
    private final InstrumentoRepository instrumentoRepository;
    private final EstudianteInstrumentoRepository estudianteInstrumentoRepository;

    public InstrumentoEstudianteService(
            EstudianteRepository estudianteRepository,
            InstrumentoRepository instrumentoRepository,
            EstudianteInstrumentoRepository estudianteInstrumentoRepository
    ) {
        this.estudianteRepository = estudianteRepository;
        this.instrumentoRepository = instrumentoRepository;
        this.estudianteInstrumentoRepository = estudianteInstrumentoRepository;
    }

    /*
     * HU-026 - Asignar un instrumento al estudiante.
     *
     * Si esPrincipal = true y ya existe un instrumento principal
     * vigente, ese registro se cierra (fecha_fin = hoy) y se crea
     * uno nuevo como principal, conservando el historial.
     *
     * Si esPrincipal = false, el instrumento simplemente se agrega
     * a la lista de instrumentos vigentes del estudiante (la HU
     * permite asignar uno o más instrumentos).
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

        boolean esPrincipal = request.getEsPrincipal() == null || request.getEsPrincipal();

        // 4. Si el instrumento nuevo es principal, cerrar el
        // instrumento principal anterior (si existe) conservando
        // su historial, tal como pide la HU al "cambiar de instrumento".
        if (esPrincipal) {
            cerrarInstrumentoPrincipalVigente(idEstudiante);
        }

        // 5. Registrar la nueva asignación con su fecha de inicio.
        EstudiantesInstrumento asignacion = crearAsignacion(
                estudiante, instrumento, esPrincipal, request.getObservaciones());

        return mapearAResponse(estudianteInstrumentoRepository.save(asignacion));
    }

    /*
     * HU-026 - Cambiar el instrumento principal del estudiante.
     *
     * Es un caso particular de "asignar" donde siempre se fija
     * esPrincipal = true, independientemente de lo que envíe el
     * cliente: expresa la intención explícita de "cambio".
     */
    @Transactional
    public InstrumentoEstudianteResponse cambiarPrincipal(Long idEstudiante, InstrumentoEstudianteRequest request) {
        request.setEsPrincipal(true);
        return asignar(idEstudiante, request);
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

    private void cerrarInstrumentoPrincipalVigente(Long idEstudiante) {
        estudianteInstrumentoRepository
                .findByIdEstudiante_IdAndEsPrincipalTrueAndFechaFinIsNull(idEstudiante)
                .ifPresent(anterior -> {
                    anterior.setFechaFin(LocalDate.now());
                    estudianteInstrumentoRepository.save(anterior);
                });
    }

    private EstudiantesInstrumento crearAsignacion(
            Estudiante estudiante, Instrumento instrumento, boolean esPrincipal, String observaciones) {

        EstudiantesInstrumento asignacion = new EstudiantesInstrumento();
        asignacion.setEstudiante(estudiante);
        asignacion.setInstrumento(instrumento);
        asignacion.setFechaInicio(LocalDate.now());
        asignacion.setEsPrincipal(esPrincipal);
        asignacion.setObservaciones(observaciones);
        return asignacion;
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

        return new InstrumentoEstudianteResponse(
                asignacion.getId(),
                asignacion.getEstudiante().getId(),
                instrumento.getId(),
                instrumento.getCodigo(),
                instrumento.getNombre(),
                asignacion.getFechaInicio(),
                asignacion.getFechaFin(),
                Boolean.TRUE.equals(asignacion.getEsPrincipal()),
                asignacion.getFechaFin() == null,
                asignacion.getObservaciones()
        );
    }
}
