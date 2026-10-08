package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.HorarioGrupo;
import cr.acusab.una.sigac.data.entity.MallaCurricular;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.PlanEstudio;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Programa;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.HorarioGrupoRepository;
import cr.acusab.una.sigac.data.repository.MallaCurricularRepository;
import cr.acusab.una.sigac.data.repository.MatriculaDetalleRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.PlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.data.repository.ProgramaRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.response.CursoDisponibleResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * HU-10 - Consultar cursos disponibles de un período académico.
 * Los cupos se calculan en el momento de la consulta para reflejar
 * los cambios de matrícula en tiempo real.
 */
@Service
public class ConsultaCursosService {

    /*
     * Igual que en la Historia 2 (matrícula): un curso creado en HU-028
     * nace con su grupo en PLANIFICADO y ninguna otra historia lo
     * publica todavía, por eso ambos estados se consideran oferta.
     */
    private static final List<String> ESTADOS_GRUPO_OFERTADOS =
            List.of("PLANIFICADO", "PUBLICADO");

    /*
     * Mismos estados que ofrece la lista de períodos del estudiante
     * (PeriodoAcademicoService.listarPeriodosDisponibles): un período
     * CERRADO o CANCELADO ya no tiene oferta que consultar.
     */
    private static final List<String> ESTADOS_PERIODO_CONSULTABLES =
            List.of("PLANIFICADO", "MATRICULA", "EN_CURSO");

    private static final String ESTADO_MATRICULA_REGISTRADA = "REGISTRADA";

    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("HH:mm");

    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final ProgramaRepository programaRepository;
    private final PlanEstudioRepository planEstudioRepository;
    private final MallaCurricularRepository mallaCurricularRepository;
    private final CursoRepository cursoRepository;
    private final GrupoRepository grupoRepository;
    private final ProfesorRepository profesorRepository;
    private final HorarioGrupoRepository horarioGrupoRepository;
    private final MatriculaDetalleRepository matriculaDetalleRepository;

    public ConsultaCursosService(
            PeriodoAcademicoRepository periodoAcademicoRepository,
            ProgramaRepository programaRepository,
            PlanEstudioRepository planEstudioRepository,
            MallaCurricularRepository mallaCurricularRepository,
            CursoRepository cursoRepository,
            GrupoRepository grupoRepository,
            ProfesorRepository profesorRepository,
            HorarioGrupoRepository horarioGrupoRepository,
            MatriculaDetalleRepository matriculaDetalleRepository
    ) {
        this.periodoAcademicoRepository = periodoAcademicoRepository;
        this.programaRepository = programaRepository;
        this.planEstudioRepository = planEstudioRepository;
        this.mallaCurricularRepository = mallaCurricularRepository;
        this.cursoRepository = cursoRepository;
        this.grupoRepository = grupoRepository;
        this.profesorRepository = profesorRepository;
        this.horarioGrupoRepository = horarioGrupoRepository;
        this.matriculaDetalleRepository = matriculaDetalleRepository;
    }

    @Transactional(readOnly = true)
    public List<CursoDisponibleResponseDTO> consultarDisponibles(
            Long idPeriodo,
            Long idPrograma,
            String texto
    ) {

        PeriodoAcademico periodo = periodoAcademicoRepository
                .findById(idPeriodo)
                .orElseThrow(() -> new BusinessException(
                        "El período académico indicado no existe."
                ));

        if (!ESTADOS_PERIODO_CONSULTABLES.contains(periodo.getEstado())) {
            throw new BusinessException(
                    "El período académico seleccionado no está disponible para consulta."
            );
        }

        Programa programa = programaRepository
                .findById(idPrograma)
                .orElseThrow(() -> new BusinessException(
                        "El programa académico indicado no existe."
                ));

        if (!Boolean.TRUE.equals(programa.getActivo())) {
            throw new BusinessException(
                    "El programa académico seleccionado se encuentra inactivo."
            );
        }

        Optional<PlanEstudio> planVigente = planEstudioRepository
                .findFirstByIdProgramaAndVigenteTrueOrderByFechaVigenciaDesdeDesc(
                        idPrograma
                );

        if (planVigente.isEmpty()) {
            return List.of();
        }

        List<MallaCurricular> mallasActivas =
                mallaCurricularRepository
                        .findByIdPlanEstudioAndActivoTrueOrderByNivelAscOrdenEnNivelAsc(
                                planVigente.get().getId()
                        );

        if (mallasActivas.isEmpty()) {
            return List.of();
        }

        Map<Long, Short> nivelPorCurso = new HashMap<>();

        for (MallaCurricular malla : mallasActivas) {
            nivelPorCurso.putIfAbsent(malla.getIdCurso(), malla.getNivel());
        }

        Map<Long, Curso> cursosActivos =
                cursoRepository.findAllById(nivelPorCurso.keySet())
                        .stream()
                        .filter(curso -> Boolean.TRUE.equals(curso.getActivo()))
                        .filter(curso -> coincideConTexto(curso, texto))
                        .collect(Collectors.toMap(Curso::getId, curso -> curso));

        if (cursosActivos.isEmpty()) {
            return List.of();
        }

        List<Grupo> grupos = grupoRepository
                .findByIdPeriodoAndEstadoIn(idPeriodo, ESTADOS_GRUPO_OFERTADOS)
                .stream()
                .filter(grupo -> cursosActivos.containsKey(grupo.getIdCurso()))
                .toList();

        if (grupos.isEmpty()) {
            return List.of();
        }

        return mapearGrupos(grupos, cursosActivos, nivelPorCurso);
    }

    private boolean coincideConTexto(Curso curso, String texto) {

        if (texto == null || texto.isBlank()) {
            return true;
        }

        String textoNormalizado = texto.trim().toLowerCase();

        return (curso.getNombre() != null
                && curso.getNombre().toLowerCase().contains(textoNormalizado))
                || (curso.getCodigo() != null
                && curso.getCodigo().toLowerCase().contains(textoNormalizado));
    }

    private List<CursoDisponibleResponseDTO> mapearGrupos(
            List<Grupo> grupos,
            Map<Long, Curso> cursos,
            Map<Long, Short> nivelPorCurso
    ) {

        List<Long> idsGrupo = grupos.stream().map(Grupo::getId).toList();

        Set<Long> idsProfesor = grupos.stream()
                .map(Grupo::getIdProfesor)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Profesor> profesores = idsProfesor.isEmpty()
                ? Map.of()
                : profesorRepository.findAllById(idsProfesor)
                .stream()
                .collect(Collectors.toMap(Profesor::getId, p -> p));

        Map<Long, List<HorarioGrupo>> horariosPorGrupo =
                horarioGrupoRepository
                        .findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(idsGrupo)
                        .stream()
                        .collect(Collectors.groupingBy(HorarioGrupo::getIdGrupo));

        Map<Long, Long> ocupadosPorGrupo = contarCuposOcupados(idsGrupo);

        List<CursoDisponibleResponseDTO> resultado = new ArrayList<>();

        for (Grupo grupo : grupos) {

            Curso curso = cursos.get(grupo.getIdCurso());

            CursoDisponibleResponseDTO dto = new CursoDisponibleResponseDTO();

            dto.setIdCurso(curso.getId());
            dto.setCodigoCurso(curso.getCodigo());
            dto.setNombreCurso(curso.getNombre());
            dto.setNivel(nivelPorCurso.get(curso.getId()));

            dto.setIdGrupo(grupo.getId());
            dto.setCodigoGrupo(grupo.getCodigo());
            dto.setModalidad(grupo.getModalidad());
            dto.setAula(grupo.getAula());

            // Los grupos creados en HU-028 aún pueden no tener profesor asignado.
            Profesor profesor = grupo.getIdProfesor() == null
                    ? null
                    : profesores.get(grupo.getIdProfesor());

            if (profesor != null) {
                dto.setProfesor(nombreCompleto(profesor));
            }

            int cupoMaximo = grupo.getCupoMaximo();
            long ocupados = ocupadosPorGrupo.getOrDefault(grupo.getId(), 0L);

            dto.setCupoMaximo(cupoMaximo);
            dto.setCupoDisponible((int) Math.max(0, cupoMaximo - ocupados));

            for (HorarioGrupo horario :
                    horariosPorGrupo.getOrDefault(grupo.getId(), List.of())) {

                CursoDisponibleResponseDTO.HorarioDTO h =
                        new CursoDisponibleResponseDTO.HorarioDTO();

                h.setDiaSemana(horario.getDiaSemana());
                h.setHoraInicio(horario.getHoraInicio().format(FORMATO_HORA));
                h.setHoraFin(horario.getHoraFin().format(FORMATO_HORA));
                h.setAula(horario.getAula());

                dto.getHorarios().add(h);
            }

            resultado.add(dto);
        }

        resultado.sort(
                Comparator
                        .comparing(
                                CursoDisponibleResponseDTO::getNivel,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(CursoDisponibleResponseDTO::getNombreCurso)
                        .thenComparing(CursoDisponibleResponseDTO::getCodigoGrupo)
        );

        return resultado;
    }

    /*
     * Cupos ocupados por grupo en tiempo real: se calculan a partir de
     * las matrículas REGISTRADAS y no retiradas, igual que en la
     * Historia 2, para reflejar los cambios más recientes.
     */
    private Map<Long, Long> contarCuposOcupados(Collection<Long> idsGrupo) {

        if (idsGrupo.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> resultado = new HashMap<>();

        for (Object[] fila : matriculaDetalleRepository
                .contarCuposOcupados(idsGrupo, ESTADO_MATRICULA_REGISTRADA)) {

            resultado.put(
                    ((Number) fila[0]).longValue(),
                    ((Number) fila[1]).longValue()
            );
        }

        return resultado;
    }

    private String nombreCompleto(Profesor profesor) {

        return Stream
                .of(profesor.getNombres(), profesor.getPrimerApellido(), profesor.getSegundoApellido())
                .filter(parte -> parte != null && !parte.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }
}
