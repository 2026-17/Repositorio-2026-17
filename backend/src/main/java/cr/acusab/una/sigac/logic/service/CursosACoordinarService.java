package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.EstudiantesInstrumento;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.MatriculaDetalle;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.EstudianteInstrumentoRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.MatriculaDetalleRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.request.CoordinacionDetalleRequest;
import cr.acusab.una.sigac.presentation.dto.response.CursoACoordinarResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * Cursos "a coordinar en Dirección" (Música de Cámara I a VI).
 *
 * Sus grupos se publican sin horario y sin profesor. El estudiante los
 * matricula como cualquier curso y después la Dirección Académica, estudiante
 * por estudiante, le asigna (o no) un profesor y anota cómo lo cumplirá.
 * Eso queda en el detalle de SU matrícula (matriculas_detalles), no en el
 * grupo: dos estudiantes del mismo grupo pueden tener profesores distintos.
 *
 * matriculas_detalles no tiene id_usuario_ultima_modificacion, así que
 * aquí no se registra quién hizo el cambio.
 */
@Service
public class CursosACoordinarService {

    private static final String ESTADO_MATRICULA_REGISTRADA = "REGISTRADA";

    // Igual que la matrícula: primero el período en matrícula; si no, el que está en curso.
    private static final List<String> ESTADOS_PERIODO_POR_DEFECTO = List.of("MATRICULA", "EN_CURSO");

    private static final int LONGITUD_MAXIMA_NOTAS = 500;

    private final GrupoRepository grupoRepository;
    private final CursoRepository cursoRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final MatriculaDetalleRepository matriculaDetalleRepository;
    private final ProfesorRepository profesorRepository;
    private final EstudianteInstrumentoRepository estudianteInstrumentoRepository;

    public CursosACoordinarService(
            GrupoRepository grupoRepository,
            CursoRepository cursoRepository,
            PeriodoAcademicoRepository periodoRepository,
            MatriculaDetalleRepository matriculaDetalleRepository,
            ProfesorRepository profesorRepository,
            EstudianteInstrumentoRepository estudianteInstrumentoRepository
    ) {
        this.grupoRepository = grupoRepository;
        this.cursoRepository = cursoRepository;
        this.periodoRepository = periodoRepository;
        this.matriculaDetalleRepository = matriculaDetalleRepository;
        this.profesorRepository = profesorRepository;
        this.estudianteInstrumentoRepository = estudianteInstrumentoRepository;
    }

    /*
     * Grupos de cursos a coordinar del período, con sus estudiantes
     * matriculados. Sin idPeriodo, el período en matrícula o en curso; si
     * no hay ninguno, la lista vacía.
     */
    @Transactional(readOnly = true)
    public List<CursoACoordinarResponse> listar(Long idPeriodo) {

        Optional<PeriodoAcademico> periodo = idPeriodo != null
                ? Optional.of(periodoRepository.findById(idPeriodo)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "El período académico indicado no existe.")))
                : periodoPorDefecto();

        if (periodo.isEmpty()) {
            return List.of();
        }

        List<Grupo> grupos = grupoRepository.findGruposACoordinar(periodo.get().getId());

        if (grupos.isEmpty()) {
            return List.of();
        }

        Map<Long, Curso> cursos = cursoRepository
                .findAllById(grupos.stream().map(Grupo::getIdCurso).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Curso::getId, Function.identity(), (a, b) -> a));

        List<MatriculaDetalle> detalles = matriculaDetalleRepository.findDetallesActivosDeGrupos(
                grupos.stream().map(Grupo::getId).toList(), ESTADO_MATRICULA_REGISTRADA);

        Map<Long, Profesor> profesores = profesoresAsignados(detalles);
        Map<Long, String> instrumentos = new HashMap<>();

        Map<Long, List<MatriculaDetalle>> porGrupo = detalles.stream()
                .collect(Collectors.groupingBy(MatriculaDetalle::getIdGrupo));

        List<CursoACoordinarResponse> resultado = new ArrayList<>();

        for (Grupo grupo : grupos) {

            Curso curso = cursos.get(grupo.getIdCurso());

            List<CursoACoordinarResponse.Estudiante> estudiantes = porGrupo
                    .getOrDefault(grupo.getId(), List.of())
                    .stream()
                    .map(d -> mapearEstudiante(d, profesores, instrumentos))
                    .sorted(Comparator.comparing(CursoACoordinarResponse.Estudiante::nombreCompleto,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            resultado.add(new CursoACoordinarResponse(
                    grupo.getId(),
                    grupo.getCodigo(),
                    grupo.getIdCurso(),
                    curso != null ? curso.getCodigo() : null,
                    curso != null ? curso.getNombre() : null,
                    grupo.getEstado(),
                    grupo.getCupoMaximo(),
                    estudiantes.size(),
                    estudiantes
            ));
        }

        resultado.sort(Comparator
                .comparing(CursoACoordinarResponse::nombreCurso, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CursoACoordinarResponse::codigo));

        return resultado;
    }

    /*
     * Asigna (o quita) el profesor de un estudiante y guarda cómo cumplirá
     * el curso. Solo en detalles vigentes de un curso a coordinar.
     */
    @Transactional
    public CursoACoordinarResponse.Estudiante actualizarDetalle(Long idDetalle, CoordinacionDetalleRequest request) {

        MatriculaDetalle detalle = matriculaDetalleRepository.findById(idDetalle)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "La matrícula indicada no existe."));

        Grupo grupo = grupoRepository.findById(detalle.getIdGrupo())
                .orElseThrow(() -> new BusinessException("No se encontró el grupo de esta matrícula."));

        Curso curso = grupo.esEspacioInstrumento() || grupo.esAgrupacion() || grupo.getIdCurso() == null
                ? null
                : cursoRepository.findById(grupo.getIdCurso()).orElse(null);

        if (curso == null || !curso.esACoordinar()) {
            throw new BusinessException(
                    "Ese curso no se coordina en Dirección: su profesor se asigna en el grupo, no por estudiante.");
        }

        if (Boolean.TRUE.equals(detalle.getRetirado())) {
            throw new BusinessException(
                    "El estudiante se retiró de este curso: ya no se le puede asignar un profesor.");
        }

        if (detalle.getMatricula() == null || detalle.getMatricula().getEstado() == null
                || !ESTADO_MATRICULA_REGISTRADA.equals(detalle.getMatricula().getEstado().getCodigo())) {
            throw new BusinessException("La matrícula de este estudiante no está activa.");
        }

        Long idProfesor = request == null ? null : request.idProfesorAsignado();

        if (idProfesor != null) {
            Profesor profesor = profesorRepository.findById(idProfesor)
                    .orElseThrow(() -> new BusinessException("El profesor seleccionado no existe."));
            if (!Boolean.TRUE.equals(profesor.getActivo())) {
                throw new BusinessException("El profesor seleccionado se encuentra inactivo.");
            }
        }

        String notas = request == null || request.notasCoordinacion() == null
                ? null : request.notasCoordinacion().trim();

        if (notas != null && notas.length() > LONGITUD_MAXIMA_NOTAS) {
            throw new BusinessException("La nota de coordinación no puede superar los 500 caracteres.");
        }

        detalle.setIdProfesorAsignado(idProfesor);
        detalle.setNotasCoordinacion(notas == null || notas.isEmpty() ? null : notas);

        MatriculaDetalle guardado = matriculaDetalleRepository.save(detalle);

        return mapearEstudiante(guardado, profesoresAsignados(List.of(guardado)), new HashMap<>());
    }

    /* =================================================================
     * Apoyo
     * ================================================================= */

    private Optional<PeriodoAcademico> periodoPorDefecto() {
        List<PeriodoAcademico> periodos = periodoRepository.findByEstadoInOrderByFechaInicioDesc(ESTADOS_PERIODO_POR_DEFECTO);
        for (String estado : ESTADOS_PERIODO_POR_DEFECTO) {
            Optional<PeriodoAcademico> conEstado = periodos.stream()
                    .filter(p -> estado.equals(p.getEstado()))
                    .findFirst();
            if (conEstado.isPresent()) {
                return conEstado;
            }
        }
        return Optional.empty();
    }

    private Map<Long, Profesor> profesoresAsignados(List<MatriculaDetalle> detalles) {
        List<Long> ids = detalles.stream()
                .map(MatriculaDetalle::getIdProfesorAsignado)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return profesorRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(Profesor::getId, Function.identity(), (a, b) -> a));
    }

    private CursoACoordinarResponse.Estudiante mapearEstudiante(
            MatriculaDetalle detalle,
            Map<Long, Profesor> profesores,
            Map<Long, String> instrumentosPorEstudiante
    ) {
        Estudiante estudiante = detalle.getMatricula() != null && detalle.getMatricula().getAutorizacion() != null
                ? detalle.getMatricula().getAutorizacion().getIdEstudiante()
                : null;

        Profesor profesor = detalle.getIdProfesorAsignado() == null
                ? null : profesores.get(detalle.getIdProfesorAsignado());

        String instrumento = estudiante == null ? null
                : instrumentosPorEstudiante.computeIfAbsent(estudiante.getId(), this::instrumentoPrincipal);

        return new CursoACoordinarResponse.Estudiante(
                detalle.getId(),
                estudiante != null ? estudiante.getId() : null,
                estudiante != null ? estudiante.getNumeroIdentificacion() : null,
                estudiante != null
                        ? nombreCompleto(estudiante.getNombres(), estudiante.getPrimerApellido(), estudiante.getSegundoApellido())
                        : null,
                instrumento,
                profesor != null ? profesor.getId() : detalle.getIdProfesorAsignado(),
                profesor != null
                        ? nombreCompleto(profesor.getNombres(), profesor.getPrimerApellido(), profesor.getSegundoApellido())
                        : null,
                detalle.getNotasCoordinacion()
        );
    }

    // Instrumento principal vigente; si no marcó ninguno, el más reciente. null si no tiene.
    private String instrumentoPrincipal(Long idEstudiante) {
        List<EstudiantesInstrumento> vigentes =
                estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(idEstudiante);
        return vigentes.stream()
                .sorted(Comparator
                        .comparing((EstudiantesInstrumento e) -> !Boolean.TRUE.equals(e.getEsPrincipal()))
                        .thenComparing(EstudiantesInstrumento::getFechaInicio,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .map(EstudiantesInstrumento::getInstrumento)
                .filter(Objects::nonNull)
                .map(i -> i.getNombre())
                .findFirst()
                .orElse(null);
    }

    private static String nombreCompleto(String nombres, String primerApellido, String segundoApellido) {
        return Stream.of(nombres, primerApellido, segundoApellido)
                .filter(parte -> parte != null && !parte.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }
}
