package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Agrupacion;
import cr.acusab.una.sigac.data.entity.AutorizacionesMatricula;
import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.EstadoMatricula;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.EstudiantePlanEstudio;
import cr.acusab.una.sigac.data.entity.EstudiantesInstrumento;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.HorarioGrupo;
import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.entity.MallaCurricular;
import cr.acusab.una.sigac.data.entity.Matricula;
import cr.acusab.una.sigac.data.entity.MatriculaDetalle;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.PlanEstudio;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.RequisitosCurso;
import cr.acusab.una.sigac.data.repository.AutorizacionesMatriculaRepository;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.EstadoMatriculaRepository;
import cr.acusab.una.sigac.data.repository.EstudianteInstrumentoRepository;
import cr.acusab.una.sigac.data.repository.EstudiantePlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.HorarioGrupoRepository;
import cr.acusab.una.sigac.data.repository.InstrumentoRepository;
import cr.acusab.una.sigac.data.repository.MallaCurricularRepository;
import cr.acusab.una.sigac.data.repository.MatriculaDetalleRepository;
import cr.acusab.una.sigac.data.repository.MatriculaRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.PlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.data.repository.RequisitosCursoRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.exception.MatriculaInvalidaException;
import cr.acusab.una.sigac.presentation.dto.request.ConfirmarSeleccionMatriculaRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.AgrupacionesDisponiblesResponseDTO;
import cr.acusab.una.sigac.presentation.dto.response.HorarioEstudianteResponseDTO;
import cr.acusab.una.sigac.presentation.dto.response.MatriculaConfirmadaResponseDTO;
import cr.acusab.una.sigac.presentation.dto.response.OfertaMatriculaResponseDTO;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * ============================================================
 * Historia 2 - Matricular cursos previamente autorizados
 * ============================================================
 *
 * Flujo completo:
 *
 * 1. El personal administrativo revisa los requisitos del estudiante y
 *    deja la autorización del período en AUTORIZADA
 *    (AutorizacionMatriculaService).
 * 2. El estudiante habilitado consulta su oferta y confirma los grupos
 *    que desea matricular, y un espacio de instrumento por cada
 *    instrumento vigente (lección individual con su profesor).
 * 3. Si todas las reglas se cumplen, la matrícula queda REGISTRADA y se
 *    descuentan los cupos, sin requerir una segunda aprobación.
 *
 * Lecciones de instrumento: los cursos Instrumento I..XIV no se ofrecen
 * en grupos. El estudiante elige un espacio libre de su profesor asignado
 * (estudiantes_instrumentos.id_profesor) y el sistema calcula el nivel:
 * el siguiente de la malla en ESE instrumento (AvanceInstrumentoService).
 * El nivel se guarda en matriculas_detalles.id_curso.
 *
 * Todo se valida aquí, en el servidor. La pantalla solo replica algunas
 * reglas para dar respuesta inmediata al estudiante.
 */
@Service
public class MatriculaEstudianteService {

    private static final String ESTADO_PERIODO_MATRICULA = "MATRICULA";
    private static final String ESTADO_PERIODO_EN_CURSO = "EN_CURSO";

    /*
     * Estados de grupo que forman parte de la oferta. Crear un curso
     * (HU-028) genera el grupo en PLANIFICADO y ninguna otra historia lo
     * publica todavía, por eso ambos estados se ofrecen.
     */
    private static final List<String> ESTADOS_GRUPO_OFERTADOS =
            List.of("PLANIFICADO", "PUBLICADO");

    private static final String ESTADO_AUTORIZACION_AUTORIZADA = "AUTORIZADA";
    private static final String ESTADO_AUTORIZACION_PENDIENTE = "PENDIENTE";
    private static final String ESTADO_AUTORIZACION_RECHAZADA = "RECHAZADA";

    private static final String ESTADO_MATRICULA_REGISTRADA = "REGISTRADA";

    // Códigos de tipos_requisitos_cursos.
    private static final String TIPO_PRERREQUISITO = "PRERREQUISITO";
    private static final String TIPO_CORREQUISITO = "CORREQUISITO";

    // Valores que entiende la pantalla.
    private static final String REQUISITO_PANTALLA = "REQUISITO";
    private static final String CORREQUISITO_PANTALLA = "CORREQUISITO";

    /*
     * Marca de sesión que entiende el trigger
     * trg_matriculas_detalles_bi_validar_requisitos. El trigger revisa los
     * correquisitos fila por fila y por eso nunca acepta dos cursos que se
     * exigen mutuamente (el primero en insertarse no encuentra al otro).
     * Este servicio ya los validó con la selección completa, así que
     * mientras inserta le pide al trigger omitir esa revisión. La marca
     * vive solo en la conexión actual y se quita siempre al terminar,
     * porque las conexiones se reutilizan.
     */
    static final String SQL_MARCAR_CORREQUISITOS_VALIDADOS =
            "SET @sigac_correquisitos_validados = 1";
    static final String SQL_QUITAR_MARCA_CORREQUISITOS =
            "SET @sigac_correquisitos_validados = NULL";

    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("HH:mm");

    /*
     * Un grupo elegido o matriculado y el curso que se cursa en él. En un
     * grupo normal es el curso del grupo; en un espacio de instrumento es
     * el nivel del estudiante (matriculas_detalles.id_curso).
     *
     * idInstrumento: en un espacio, el instrumento que el estudiante
     * cursa (matriculas_detalles.id_instrumento, o el de la lección que
     * eligió). NO se lee del grupo: un espacio de "cualquier instrumento
     * del profesor" no tiene instrumento. En un grupo normal es null.
     */
    /*
     * En una agrupación, idCurso es el nivel calculado (Conjunto
     * Instrumental o Coro), agrupacion es la del catálogo y orden es el
     * orden en que el estudiante la eligió (regla D4). El nivel es interno:
     * al estudiante solo se le muestra el nombre de la agrupación.
     */
    /*
     * idProfesorAsignado: en un curso a coordinar en Dirección, el profesor
     * que la Dirección le asignó al estudiante en su matrícula (solo en lo
     * ya matriculado; null = sin asignar).
     */
    private record Seleccion(Grupo grupo, Long idCurso, Integer idInstrumento, Agrupacion agrupacion, Short orden,
                             Long idProfesorAsignado) {

        Seleccion(Grupo grupo, Long idCurso, Integer idInstrumento, Agrupacion agrupacion, Short orden) {
            this(grupo, idCurso, idInstrumento, agrupacion, orden, null);
        }

        Seleccion(Grupo grupo, Long idCurso, Integer idInstrumento) {
            this(grupo, idCurso, idInstrumento, null, null);
        }

        static Seleccion deGrupo(Grupo grupo) {
            return new Seleccion(grupo, grupo.getIdCurso(), null);
        }

        boolean esEspacio() {
            return grupo.esEspacioInstrumento();
        }

        boolean esAgrupacion() {
            return grupo.esAgrupacion();
        }

        String nombreAgrupacion() {
            return agrupacion != null ? agrupacion.getNombre() : grupo.getCodigo();
        }

        boolean esInstrumental() {
            return esAgrupacion() && agrupacion != null && agrupacion.esInstrumental();
        }

        boolean esCoral() {
            return esAgrupacion() && agrupacion != null && agrupacion.esCoral();
        }

        /*
         * Un mismo nivel de instrumento puede llevarse en dos instrumentos, y
         * un mismo nivel de Conjunto Instrumental en dos agrupaciones.
         */
        String clave() {
            if (esAgrupacion()) {
                return "AGR|" + grupo.getIdAgrupacion();
            }
            return esEspacio() ? idCurso + "|" + idInstrumento : String.valueOf(idCurso);
        }
    }

    /*
     * Lección de un instrumento vigente: el nivel que le corresponde, por
     * qué, los prerrequisitos que le faltan y el espacio que ya tiene en
     * este período (si lo tiene).
     */
    private record Leccion(
            EstudiantesInstrumento asignacion,
            Curso nivel,
            String motivoNivel,
            boolean completado,
            List<String> prerrequisitosIncumplidos,
            Seleccion matriculada
    ) {

        Integer idInstrumento() {
            return asignacion.getInstrumento().getId();
        }

        String nombreInstrumento() {
            return asignacion.getInstrumento().getNombre();
        }

        Profesor profesor() {
            return asignacion.getProfesor();
        }

        boolean requiereEspacio() {
            return nivel != null && matriculada == null;
        }
    }

    private final EstudianteRepository estudianteRepository;
    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final AutorizacionesMatriculaRepository autorizacionRepository;
    private final EstudiantePlanEstudioRepository estudiantePlanRepository;
    private final PlanEstudioRepository planEstudioRepository;
    private final MallaCurricularRepository mallaRepository;
    private final CursoRepository cursoRepository;
    private final GrupoRepository grupoRepository;
    private final HorarioGrupoRepository horarioGrupoRepository;
    private final ProfesorRepository profesorRepository;
    private final RequisitosCursoRepository requisitosCursoRepository;
    private final MatriculaRepository matriculaRepository;
    private final MatriculaDetalleRepository matriculaDetalleRepository;
    private final EstadoMatriculaRepository estadoMatriculaRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AvanceInstrumentoService avanceInstrumentoService;
    private final EstudianteInstrumentoRepository estudianteInstrumentoRepository;
    private final InstrumentoRepository instrumentoRepository;
    private final AvanceAgrupacionService avanceAgrupacionService;
    private final AgrupacionService agrupacionService;

    public MatriculaEstudianteService(
            EstudianteRepository estudianteRepository,
            PeriodoAcademicoRepository periodoAcademicoRepository,
            AutorizacionesMatriculaRepository autorizacionRepository,
            EstudiantePlanEstudioRepository estudiantePlanRepository,
            PlanEstudioRepository planEstudioRepository,
            MallaCurricularRepository mallaRepository,
            CursoRepository cursoRepository,
            GrupoRepository grupoRepository,
            HorarioGrupoRepository horarioGrupoRepository,
            ProfesorRepository profesorRepository,
            RequisitosCursoRepository requisitosCursoRepository,
            MatriculaRepository matriculaRepository,
            MatriculaDetalleRepository matriculaDetalleRepository,
            EstadoMatriculaRepository estadoMatriculaRepository,
            JdbcTemplate jdbcTemplate,
            AvanceInstrumentoService avanceInstrumentoService,
            EstudianteInstrumentoRepository estudianteInstrumentoRepository,
            InstrumentoRepository instrumentoRepository,
            AvanceAgrupacionService avanceAgrupacionService,
            AgrupacionService agrupacionService
    ) {
        this.estudianteRepository = estudianteRepository;
        this.periodoAcademicoRepository = periodoAcademicoRepository;
        this.autorizacionRepository = autorizacionRepository;
        this.estudiantePlanRepository = estudiantePlanRepository;
        this.planEstudioRepository = planEstudioRepository;
        this.mallaRepository = mallaRepository;
        this.cursoRepository = cursoRepository;
        this.grupoRepository = grupoRepository;
        this.horarioGrupoRepository = horarioGrupoRepository;
        this.profesorRepository = profesorRepository;
        this.requisitosCursoRepository = requisitosCursoRepository;
        this.matriculaRepository = matriculaRepository;
        this.matriculaDetalleRepository = matriculaDetalleRepository;
        this.estadoMatriculaRepository = estadoMatriculaRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.avanceInstrumentoService = avanceInstrumentoService;
        this.estudianteInstrumentoRepository = estudianteInstrumentoRepository;
        this.instrumentoRepository = instrumentoRepository;
        this.avanceAgrupacionService = avanceAgrupacionService;
        this.agrupacionService = agrupacionService;
    }

    /*
     * ============================================================
     * Consultar mi oferta de matrícula
     * ============================================================
     *
     * Nunca falla por falta de habilitación: en ese caso devuelve
     * habilitado = false y el motivo, para que la pantalla lo informe.
     */
    @Transactional(readOnly = true)
    public OfertaMatriculaResponseDTO consultarMiOferta() {

        Estudiante estudiante = obtenerEstudianteAutenticado();

        OfertaMatriculaResponseDTO respuesta = new OfertaMatriculaResponseDTO();
        respuesta.setEstudiante(mapearEstudiante(estudiante));

        if (!Boolean.TRUE.equals(estudiante.getActivo())) {
            return noHabilitado(respuesta, "Solo un estudiante en estado «Activo» puede matricular cursos.");
        }

        Optional<PeriodoAcademico> periodoVigente = buscarPeriodoVigente();

        if (periodoVigente.isEmpty()) {
            return noHabilitado(respuesta, "No hay un período académico en etapa de matrícula actualmente.");
        }

        PeriodoAcademico periodo = periodoVigente.get();
        respuesta.setPeriodo(mapearPeriodo(periodo));

        Optional<AutorizacionesMatricula> autorizacion =
                autorizacionRepository.findByIdEstudiante_IdAndIdPeriodo_Id(estudiante.getId(), periodo.getId());

        String motivoAutorizacion = motivoSinAutorizacion(autorizacion);

        if (motivoAutorizacion != null) {
            return noHabilitado(respuesta, motivoAutorizacion);
        }

        List<Long> idsPlan = idsPlanesInscritos(estudiante.getId());

        if (idsPlan.isEmpty()) {
            return noHabilitado(respuesta,
                    "No está inscrito en ningún plan de estudio. Comuníquese con la administración.");
        }

        respuesta.setHabilitado(true);

        Map<Long, PlanEstudio> planes = planesPorId(idsPlan);

        planes.values()
                .stream()
                .sorted(Comparator.comparing(PlanEstudio::getNombre))
                .forEach(plan -> respuesta.getPlanes().add(mapearPlan(plan)));

        Map<Long, MallaCurricular> mallaPorCurso = mallaPorCurso(idsPlan);

        // Los cursos de instrumento y los de agrupación (CI, Coro) no se
        // ofrecen como grupos normales.
        Set<Long> cursosInstrumento = cursosDeInstrumento(mallaPorCurso.keySet());

        List<Grupo> gruposOfertados = grupoRepository
                .findByIdPeriodoAndEstadoIn(periodo.getId(), ESTADOS_GRUPO_OFERTADOS)
                .stream()
                .filter(grupo -> !grupo.esEspacioInstrumento())
                // Las agrupaciones tienen su propio paso (consultarMisAgrupaciones).
                .filter(grupo -> !grupo.esAgrupacion())
                .filter(grupo -> mallaPorCurso.containsKey(grupo.getIdCurso()))
                .filter(grupo -> !cursosInstrumento.contains(grupo.getIdCurso()))
                .toList();

        List<OfertaMatriculaResponseDTO.GrupoDTO> oferta = new ArrayList<>(
                mapearSelecciones(gruposOfertados.stream().map(Seleccion::deGrupo).toList(), mallaPorCurso, planes));

        // D7: los grupos cuyo curso tiene una edad que no es la del estudiante.
        Integer edad = ReglasEdad.edadAl(estudiante.getFechaNacimiento(), periodo.getFechaInicio());
        Map<Long, Curso> cursosOferta = cursoRepository.findAllById(
                        gruposOfertados.stream().map(Grupo::getIdCurso).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Curso::getId, Function.identity(), (a, b) -> a));
        oferta.forEach(grupo -> {
            Curso curso = cursosOferta.get(grupo.getIdCurso());
            if (curso != null) {
                grupo.setMotivoNoDisponible(ReglasEdad.motivoBloqueo(curso.getEdadMinima(), curso.getEdadMaxima(), edad));
            }
        });

        oferta.sort(Comparator
                .comparing(OfertaMatriculaResponseDTO.GrupoDTO::getNivel,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(OfertaMatriculaResponseDTO.GrupoDTO::getNombreCurso)
                .thenComparing(OfertaMatriculaResponseDTO.GrupoDTO::getCodigo));

        respuesta.setOferta(oferta);

        List<Seleccion> yaMatriculados = seleccionesYaMatriculadas(autorizacion.orElseThrow());

        respuesta.setMatriculados(mapearSelecciones(yaMatriculados, mallaPorCurso, planes));

        Set<Long> idsCursoOferta = gruposOfertados.stream().map(Grupo::getIdCurso).collect(Collectors.toSet());

        // Lecciones de instrumento, con los espacios libres de su profesor.
        List<Leccion> lecciones = calcularLecciones(estudiante.getId(), idsPlan, yaMatriculados);

        for (Leccion leccion : lecciones) {
            respuesta.getLeccionesInstrumento().add(
                    mapearLeccion(leccion, periodo, mallaPorCurso, planes));
            if (leccion.nivel() != null) {
                idsCursoOferta.add(leccion.nivel().getId());
            }
        }

        respuesta.setRequisitosPorCurso(mapearRequisitos(idsPlan, idsCursoOferta));

        aprobadosDelPlan(estudiante.getId()).forEach((idCurso, aprobado) -> {
            OfertaMatriculaResponseDTO.CursoAprobadoDTO dto = new OfertaMatriculaResponseDTO.CursoAprobadoDTO();
            dto.setIdCurso(idCurso);
            dto.setNota(aprobado.nota());
            respuesta.getCursosAprobados().add(dto);
        });

        return respuesta;
    }

    /*
     * ============================================================
     * Agrupaciones disponibles (paso "Agrupaciones" de la matrícula)
     * ============================================================
     *
     * Grupos de agrupación PUBLICADOS del período vigente, con su horario,
     * profesores, cupos y, si no se pueden elegir, el motivo. El nivel que
     * le correspondería NO se devuelve: es interno.
     */
    @Transactional(readOnly = true)
    public AgrupacionesDisponiblesResponseDTO consultarMisAgrupaciones() {

        Estudiante estudiante = obtenerEstudianteAutenticado();
        AgrupacionesDisponiblesResponseDTO respuesta = new AgrupacionesDisponiblesResponseDTO();

        if (!Boolean.TRUE.equals(estudiante.getActivo())) {
            respuesta.setMotivoNoHabilitado("Solo un estudiante en estado «Activo» puede matricular cursos.");
            return respuesta;
        }

        Optional<PeriodoAcademico> periodoVigente = buscarPeriodoVigente();
        if (periodoVigente.isEmpty()) {
            respuesta.setMotivoNoHabilitado("No hay un período académico en etapa de matrícula actualmente.");
            return respuesta;
        }

        PeriodoAcademico periodo = periodoVigente.get();
        respuesta.setPeriodo(mapearPeriodo(periodo));

        Optional<AutorizacionesMatricula> autorizacion =
                autorizacionRepository.findByIdEstudiante_IdAndIdPeriodo_Id(estudiante.getId(), periodo.getId());
        String motivoAutorizacion = motivoSinAutorizacion(autorizacion);
        if (motivoAutorizacion != null) {
            respuesta.setMotivoNoHabilitado(motivoAutorizacion);
            return respuesta;
        }

        respuesta.setHabilitado(true);

        List<Seleccion> yaMatriculadas = seleccionesYaMatriculadas(autorizacion.orElseThrow())
                .stream()
                .filter(Seleccion::esAgrupacion)
                .toList();

        Set<Long> gruposMatriculados = yaMatriculadas.stream().map(s -> s.grupo().getId()).collect(Collectors.toSet());

        // Publicados del período y los que ya tiene matriculados (aunque cambiaran de estado).
        Map<Long, Grupo> grupos = new LinkedHashMap<>();
        grupoRepository.findGruposAgrupacion(periodo.getId()).stream()
                .filter(g -> ReglasAgrupaciones.ESTADO_GRUPO_OFERTADO.equals(g.getEstado())
                        || gruposMatriculados.contains(g.getId()))
                .forEach(g -> grupos.put(g.getId(), g));
        yaMatriculadas.forEach(s -> grupos.putIfAbsent(s.grupo().getId(), s.grupo()));

        if (grupos.isEmpty()) {
            return respuesta;
        }

        Map<Long, Agrupacion> agrupaciones = agrupacionService.agrupacionesPorId(
                grupos.values().stream().map(Grupo::getIdAgrupacion).toList());

        DatosAgrupaciones datos = new DatosAgrupaciones(estudiante, periodo, agrupaciones.keySet());
        Map<Long, List<String>> profesores = agrupacionService.nombresProfesores(grupos.values());
        Map<Long, List<HorarioGrupo>> horarios = horariosPorGrupo(grupos.keySet());
        Map<Long, Long> ocupados = contarCuposOcupados(grupos.keySet());

        Set<Long> agrupacionesMatriculadas = yaMatriculadas.stream()
                .map(s -> s.grupo().getIdAgrupacion())
                .collect(Collectors.toSet());


        for (Grupo grupo : grupos.values()) {

            Agrupacion agrupacion = agrupaciones.get(grupo.getIdAgrupacion());
            boolean matriculada = gruposMatriculados.contains(grupo.getId());

            if (agrupacion == null || (!matriculada && !Boolean.TRUE.equals(agrupacion.getActiva()))) {
                continue;
            }

            AgrupacionesDisponiblesResponseDTO.AgrupacionDTO dto = new AgrupacionesDisponiblesResponseDTO.AgrupacionDTO();
            dto.setIdGrupo(grupo.getId());
            dto.setCodigo(grupo.getCodigo());
            dto.setIdAgrupacion(agrupacion.getId());
            dto.setNombre(agrupacion.getNombre());
            dto.setTipo(agrupacion.getTipo());
            dto.setDescripcion(agrupacion.getDescripcion());
            dto.setAula(grupo.getAula());
            dto.setProfesores(profesores.getOrDefault(grupo.getId(), List.of()));
            dto.setHorarios(mapearHorarios(horarios.getOrDefault(grupo.getId(), List.of())));

            long ocupadosGrupo = ocupados.getOrDefault(grupo.getId(), 0L);
            dto.setCupoMaximo(grupo.getCupoMaximo());
            dto.setCupoDisponible((int) Math.max(0, grupo.getCupoMaximo() - ocupadosGrupo));
            dto.setMatriculada(matriculada);

            if (!matriculada) {
                String motivo = agrupacionesMatriculadas.contains(agrupacion.getId())
                        ? "Ya está matriculado en esta agrupación en este período."
                        : motivoBloqueoAgrupacion(agrupacion, datos);
                if (motivo == null && ocupadosGrupo >= grupo.getCupoMaximo()) {
                    motivo = "No tiene cupos libres.";
                }
                dto.setBloqueada(motivo != null);
                dto.setMotivoBloqueo(motivo);
            }

            respuesta.getAgrupaciones().add(dto);
        }

        respuesta.getAgrupaciones().sort(Comparator
                .comparing(AgrupacionesDisponiblesResponseDTO.AgrupacionDTO::getTipo,
                        Comparator.comparing(tipo -> !Agrupacion.TIPO_INSTRUMENTAL.equals(tipo)))
                .thenComparing(AgrupacionesDisponiblesResponseDTO.AgrupacionDTO::getNombre)
                .thenComparing(AgrupacionesDisponiblesResponseDTO.AgrupacionDTO::getCodigo));

        return respuesta;
    }

    /*
     * ============================================================
     * Horario del estudiante
     * ============================================================
     *
     * Grupos matriculados (no retirados) del estudiante autenticado en
     * un período, con sus bloques de horario. A diferencia de la oferta,
     * funciona en cualquier etapa del período (también "En curso").
     *
     * Si no se indica idPeriodo (o no es uno en el que tenga cursos), se
     * muestra el período en curso; si no hay, el que está en matrícula;
     * si tampoco, el más reciente.
     *
     * Un espacio de instrumento se muestra con su nivel ("Instrumento VI
     * · Piano") y espacioInstrumento = true.
     */
    @Transactional(readOnly = true)
    public HorarioEstudianteResponseDTO consultarMiHorario(Long idPeriodo) {

        Estudiante estudiante = obtenerEstudianteAutenticado();

        HorarioEstudianteResponseDTO respuesta = new HorarioEstudianteResponseDTO();
        respuesta.setEstudiante(mapearEstudiante(estudiante));

        // Períodos en los que tiene cursos matriculados, del más reciente
        // al más antiguo, con los grupos de cada uno.
        Map<PeriodoAcademico, List<Seleccion>> porPeriodo = new LinkedHashMap<>();

        autorizacionRepository
                .findByIdEstudiante_Id(estudiante.getId())
                .stream()
                .sorted(Comparator.comparing(
                        (AutorizacionesMatricula a) -> a.getIdPeriodo().getFechaInicio()
                ).reversed())
                .forEach(autorizacion -> {
                    List<Seleccion> selecciones = seleccionesYaMatriculadas(autorizacion);
                    if (!selecciones.isEmpty()) {
                        porPeriodo.put(autorizacion.getIdPeriodo(), selecciones);
                    }
                });

        if (porPeriodo.isEmpty()) {
            return respuesta;
        }

        porPeriodo.keySet().forEach(periodo -> respuesta.getPeriodos().add(mapearPeriodoHorario(periodo)));

        PeriodoAcademico periodo = elegirPeriodoHorario(porPeriodo.keySet(), idPeriodo);

        respuesta.setPeriodo(mapearPeriodoHorario(periodo));

        // El nivel y el plan de cada curso salen de la malla de los planes
        // en los que está inscrito (igual que en la oferta).
        List<Long> idsPlan = idsPlanesInscritos(estudiante.getId());

        List<OfertaMatriculaResponseDTO.GrupoDTO> clases = new ArrayList<>(
                mapearSelecciones(porPeriodo.get(periodo), mallaPorCurso(idsPlan), planesPorId(idsPlan)));

        clases.sort(Comparator.comparing(
                OfertaMatriculaResponseDTO.GrupoDTO::getNombreCurso,
                Comparator.nullsLast(Comparator.naturalOrder())));

        respuesta.setClases(clases);

        return respuesta;
    }

    private PeriodoAcademico elegirPeriodoHorario(Collection<PeriodoAcademico> periodos, Long idPeriodo) {

        if (idPeriodo != null) {
            Optional<PeriodoAcademico> pedido = periodos.stream()
                    .filter(periodo -> idPeriodo.equals(periodo.getId()))
                    .findFirst();

            if (pedido.isPresent()) {
                return pedido.get();
            }
        }

        for (String estado : List.of(ESTADO_PERIODO_EN_CURSO, ESTADO_PERIODO_MATRICULA)) {
            Optional<PeriodoAcademico> conEstado = periodos.stream()
                    .filter(periodo -> estado.equals(periodo.getEstado()))
                    .findFirst();

            if (conEstado.isPresent()) {
                return conEstado.get();
            }
        }

        // Vienen ordenados del más reciente al más antiguo.
        return periodos.iterator().next();
    }

    private HorarioEstudianteResponseDTO.PeriodoDTO mapearPeriodoHorario(PeriodoAcademico periodo) {

        HorarioEstudianteResponseDTO.PeriodoDTO dto = new HorarioEstudianteResponseDTO.PeriodoDTO();

        dto.setId(periodo.getId());
        dto.setCodigo(periodo.getCodigo());
        dto.setNombre(periodo.getNombre());
        dto.setEstado(periodo.getEstado());
        dto.setFechaInicio(periodo.getFechaInicio());
        dto.setFechaFin(periodo.getFechaFin());

        return dto;
    }

    /*
     * ============================================================
     * Confirmar la selección y registrar la matrícula
     * ============================================================
     *
     * Vuelve a verificar todo dentro de la transacción, con los grupos y
     * espacios bloqueados, para que dos confirmaciones simultáneas no
     * ocupen el mismo último cupo ni el mismo espacio de instrumento.
     *
     * READ_COMMITTED es necesario: con el REPEATABLE READ por defecto de
     * MySQL, la transacción que espera el bloqueo seguiría viendo los
     * cupos de antes de que la otra confirmara y sobrevendería el grupo.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MatriculaConfirmadaResponseDTO confirmar(ConfirmarSeleccionMatriculaRequestDTO request) {

        List<Long> idsGrupo = request == null || request.getIdsGrupo() == null
                ? List.of() : request.getIdsGrupo();

        /*
         * Espacios: idsEspacio (el instrumento se deduce) y espacios
         * ({idEspacio, idInstrumento}: la lección para la que se eligió,
         * necesaria en un espacio de varios instrumentos si el estudiante
         * tiene dos lecciones con ese profesor).
         */
        List<Long> idsEspacio = new ArrayList<>(request == null || request.getIdsEspacio() == null
                ? List.of() : request.getIdsEspacio());
        Map<Long, Integer> instrumentoPedido = new HashMap<>();
        if (request != null && request.getEspacios() != null) {
            for (ConfirmarSeleccionMatriculaRequestDTO.EspacioElegido elegido : request.getEspacios()) {
                if (elegido == null) {
                    idsEspacio.add(null);
                    continue;
                }
                idsEspacio.add(elegido.getIdEspacio());
                if (elegido.getIdEspacio() != null && elegido.getIdInstrumento() != null) {
                    instrumentoPedido.put(elegido.getIdEspacio(), elegido.getIdInstrumento());
                }
            }
        }

        // En el orden en que el estudiante las marcó (regla D4).
        List<Long> idsAgrupacion = request == null || request.getIdsAgrupacion() == null
                ? List.of() : request.getIdsAgrupacion();

        if (idsGrupo.isEmpty() && idsEspacio.isEmpty() && idsAgrupacion.isEmpty()) {
            throw new BusinessException("Debe seleccionar al menos un curso para matricular.");
        }

        List<Long> todos = new ArrayList<>(idsGrupo);
        todos.addAll(idsEspacio);
        todos.addAll(idsAgrupacion);

        if (todos.stream().anyMatch(Objects::isNull) || todos.stream().distinct().count() != todos.size()) {
            throw new BusinessException("La selección contiene grupos repetidos o no válidos.");
        }

        Estudiante estudiante = obtenerEstudianteAutenticado();

        if (!Boolean.TRUE.equals(estudiante.getActivo())) {
            throw new BusinessException("Solo un estudiante en estado «Activo» puede matricular cursos.");
        }

        PeriodoAcademico periodo = buscarPeriodoVigente()
                .orElseThrow(() -> new BusinessException(
                        "No hay un período académico en etapa de matrícula actualmente."));

        /*
         * La habilitación se verifica de nuevo al confirmar: pudo
         * anularse después de que el estudiante abrió la pantalla.
         */
        Optional<AutorizacionesMatricula> autorizacionOpt =
                autorizacionRepository.findByIdEstudiante_IdAndIdPeriodo_Id(estudiante.getId(), periodo.getId());

        String motivoAutorizacion = motivoSinAutorizacion(autorizacionOpt);

        if (motivoAutorizacion != null) {
            throw new BusinessException(motivoAutorizacion);
        }

        AutorizacionesMatricula autorizacion = autorizacionOpt.orElseThrow();

        List<Long> idsPlan = idsPlanesInscritos(estudiante.getId());

        if (idsPlan.isEmpty()) {
            throw new BusinessException(
                    "No está inscrito en ningún plan de estudio. Comuníquese con la administración.");
        }

        /*
         * Se bloquean los grupos y espacios ANTES de leer la matrícula y
         * los cupos: quien llega segundo espera aquí y, con READ_COMMITTED,
         * ya ve lo que confirmó quien llegó primero.
         */
        List<Grupo> bloqueados = grupoRepository.findAllByIdParaActualizar(todos);

        if (bloqueados.size() != todos.size()) {
            throw new BusinessException("Uno o más de los grupos seleccionados no existen.");
        }

        Map<Long, Grupo> gruposPorId = bloqueados.stream()
                .collect(Collectors.toMap(Grupo::getId, Function.identity()));

        Matricula matriculaExistente = matriculaRepository.findByAutorizacion_Id(autorizacion.getId()).orElse(null);

        if (matriculaExistente != null
                && !ESTADO_MATRICULA_REGISTRADA.equals(matriculaExistente.getEstado().getCodigo())) {
            throw new BusinessException("Su matrícula de este período no se encuentra activa.");
        }

        Map<Long, MallaCurricular> mallaPorCurso = mallaPorCurso(idsPlan);

        List<Seleccion> yaMatriculados = matriculaExistente == null
                ? List.of() : seleccionesDeMatricula(matriculaExistente);

        Set<String> errores = new LinkedHashSet<>();

        // Lecciones de instrumento: a cada espacio le corresponde el nivel
        // calculado de su instrumento.
        List<Leccion> lecciones = calcularLecciones(estudiante.getId(), idsPlan, yaMatriculados);

        List<Seleccion> seleccionadas = new ArrayList<>();

        for (Long idGrupo : idsGrupo) {
            Grupo grupo = gruposPorId.get(idGrupo);
            if (grupo.esEspacioInstrumento()) {
                errores.add("El grupo " + grupo.getCodigo()
                        + " es un espacio de instrumento: elíjalo en la sección de lecciones de instrumento.");
                continue;
            }
            if (grupo.esAgrupacion()) {
                errores.add("El grupo " + grupo.getCodigo()
                        + " es una agrupación: elíjala en el paso de agrupaciones.");
                continue;
            }
            seleccionadas.add(Seleccion.deGrupo(grupo));
        }

        seleccionadas.addAll(asignarNiveles(idsEspacio, instrumentoPedido, gruposPorId, lecciones, errores));

        // Agrupaciones: el nivel de cada una lo calcula el sistema (D1 a D7).
        seleccionadas.addAll(asignarNivelesAgrupacion(
                estudiante, periodo, idsAgrupacion, gruposPorId, yaMatriculados, errores));

        validarLeccionesCompletas(lecciones, seleccionadas, errores);

        errores.addAll(validarSeleccion(estudiante, periodo, idsPlan, mallaPorCurso, seleccionadas, yaMatriculados));

        if (!errores.isEmpty()) {
            throw new MatriculaInvalidaException(new ArrayList<>(errores));
        }

        List<Seleccion> paraInsertar = ordenarCorrequisitosPrimero(seleccionadas, idsPlan);

        Matricula matricula;

        try {

            // El plan es el del primer curso elegido que esté en una malla
            // (una agrupación puede no estarlo).
            Long idPlanMatricula = seleccionadas.stream()
                    .map(s -> mallaPorCurso.get(s.idCurso()))
                    .filter(Objects::nonNull)
                    .map(MallaCurricular::getIdPlanEstudio)
                    .findFirst()
                    .orElse(idsPlan.get(0));

            matricula = matriculaExistente != null
                    ? matriculaExistente
                    : crearMatricula(autorizacion, idPlanMatricula);

            /*
             * Los correquisitos ya se validaron arriba con la selección
             * completa (validarRequisitos). Se le avisa al trigger para que
             * no los vuelva a revisar fila por fila, lo que haría imposible
             * matricular cursos que son correquisitos mutuos.
             */
            jdbcTemplate.execute(SQL_MARCAR_CORREQUISITOS_VALIDADOS);

            try {

                for (Seleccion seleccion : paraInsertar) {

                    MatriculaDetalle detalle = new MatriculaDetalle();
                    detalle.setMatricula(matricula);
                    detalle.setIdGrupo(seleccion.grupo().getId());

                    // En un espacio o una agrupación, el nivel que se cursa.
                    detalle.setIdCurso(seleccion.esEspacio() || seleccion.esAgrupacion()
                            ? seleccion.idCurso() : null);

                    // En un espacio, el instrumento que se cursa.
                    detalle.setIdInstrumento(seleccion.esEspacio() ? seleccion.idInstrumento() : null);

                    // En una agrupación, el orden en que se eligió (D4).
                    detalle.setOrdenSeleccion(seleccion.esAgrupacion() ? seleccion.orden() : null);

                    matriculaDetalleRepository.save(detalle);
                }

                matriculaDetalleRepository.flush();

            } finally {
                jdbcTemplate.execute(SQL_QUITAR_MARCA_CORREQUISITOS);
            }

        } catch (BusinessException ex) {

            throw ex;

        } catch (RuntimeException ex) {

            /*
             * Los triggers de la base de datos son la última línea de
             * defensa. Si rechazan la matrícula, se le muestra su mensaje
             * al estudiante en lugar de un error interno.
             */
            String reglaDeBaseDeDatos = mensajeDeReglaDeBaseDeDatos(ex);

            if (reglaDeBaseDeDatos != null) {
                throw new MatriculaInvalidaException(List.of(reglaDeBaseDeDatos));
            }

            throw ex;
        }

        MatriculaConfirmadaResponseDTO respuesta = new MatriculaConfirmadaResponseDTO();

        respuesta.setIdMatricula(matricula.getId());
        respuesta.setEstado(matricula.getEstado().getCodigo());
        respuesta.setFechaMatricula(matricula.getFechaMatricula());
        respuesta.setPeriodo(mapearPeriodo(periodo));
        respuesta.setCursos(mapearSelecciones(seleccionadas, mallaPorCurso, planesPorId(idsPlan)));

        return respuesta;
    }

    /*
     * ============================================================
     * Lecciones de instrumento
     * ============================================================
     */

    /*
     * Por cada instrumento vigente, el nivel que le corresponde (el
     * siguiente de la malla en ese instrumento) y el espacio que ya tenga
     * matriculado en el período. Vacío si ningún plan del estudiante
     * tiene cursos de instrumento.
     */
    private List<Leccion> calcularLecciones(Long idEstudiante, List<Long> idsPlan, List<Seleccion> yaMatriculados) {

        List<EstudiantesInstrumento> vigentes =
                estudianteInstrumentoRepository.findByIdEstudiante_IdAndFechaFinIsNull(idEstudiante);

        if (vigentes.isEmpty()) {
            return List.of();
        }

        Optional<Long> idPlan = avanceInstrumentoService.planConInstrumento(idsPlan);

        if (idPlan.isEmpty()) {
            return List.of();
        }

        List<Curso> nivelesPlan = avanceInstrumentoService.nivelesDelPlan(idPlan.get());

        List<Leccion> lecciones = new ArrayList<>();

        for (EstudiantesInstrumento asignacion : vigentes.stream()
                .sorted(Comparator
                        .comparing((EstudiantesInstrumento e) -> !Boolean.TRUE.equals(e.getEsPrincipal()))
                        .thenComparing(EstudiantesInstrumento::getFechaInicio)
                        .thenComparing(EstudiantesInstrumento::getId))
                .toList()) {

            Integer idInstrumento = asignacion.getInstrumento().getId();
            String nombreInstrumento = asignacion.getInstrumento().getNombre();

            Map<Long, AvanceInstrumentoService.NivelAprobado> aprobados =
                    avanceInstrumentoService.nivelesAprobados(idEstudiante, idInstrumento);

            Seleccion matriculada = yaMatriculados.stream()
                    .filter(Seleccion::esEspacio)
                    .filter(s -> Objects.equals(s.idInstrumento(), idInstrumento))
                    .findFirst()
                    .orElse(null);

            int posicion = -1;
            for (int i = 0; i < nivelesPlan.size(); i++) {
                if (!aprobados.containsKey(nivelesPlan.get(i).getId())) {
                    posicion = i;
                    break;
                }
            }

            // Si ya tiene un espacio este período, su nivel es el matriculado.
            Curso nivel = posicion >= 0 ? nivelesPlan.get(posicion) : null;
            if (matriculada != null) {
                Long idNivel = matriculada.idCurso();
                nivel = nivelesPlan.stream().filter(c -> c.getId().equals(idNivel)).findFirst().orElse(nivel);
            }

            String motivo;
            if (nivel == null) {
                motivo = "Completó todos los niveles de " + nombreInstrumento + " del plan.";
            } else {
                int indice = nivelesPlan.indexOf(nivel);
                motivo = motivoNivel(nivel, indice > 0 ? nivelesPlan.get(indice - 1) : null,
                        indice > 0 ? aprobados.get(nivelesPlan.get(indice - 1).getId()) : null,
                        nombreInstrumento);
            }

            List<String> faltantes = nivel == null || matriculada != null
                    ? List.of()
                    : prerrequisitosIncumplidos(idEstudiante, idsPlan, nivel, idInstrumento);

            lecciones.add(new Leccion(asignacion, nivel, motivo, nivel == null, faltantes, matriculada));
        }

        return lecciones;
    }

    // Por qué le corresponde ese nivel ("Aprobó Instrumento V en el II ciclo 2026.").
    private String motivoNivel(
            Curso nivel, Curso anterior, AvanceInstrumentoService.NivelAprobado aprobadoAnterior, String instrumento) {

        if (anterior == null || aprobadoAnterior == null) {
            return "Es su primer nivel de " + instrumento + ".";
        }

        if (AvanceInstrumentoService.ORIGEN_SIGAC.equals(aprobadoAnterior.origen())) {
            String periodo = aprobadoAnterior.idPeriodo() == null ? null
                    : periodoAcademicoRepository.findById(aprobadoAnterior.idPeriodo())
                    .map(PeriodoAcademico::getNombre).orElse(null);
            return "Aprobó " + anterior.getNombre() + (periodo != null ? " en el " + periodo : "") + ".";
        }

        if ("NIVEL_INGRESO".equals(aprobadoAnterior.origen())) {
            return "Ingresó en " + nivel.getNombre()
                    + " por prueba de ubicación; los niveles anteriores están convalidados.";
        }

        return "Tiene aprobado " + anterior.getNombre() + " por certificación anterior a SIGAC.";
    }

    // Prerrequisitos del nivel, evaluados con lo aprobado en ESE instrumento.
    private List<String> prerrequisitosIncumplidos(
            Long idEstudiante, List<Long> idsPlan, Curso nivel, Integer idInstrumento) {

        Map<Long, AvanceInstrumentoService.CursoAprobado> aprobados =
                aprobadosParaInstrumento(idEstudiante, idInstrumento);

        Set<String> faltantes = new LinkedHashSet<>();

        for (RequisitosCurso requisito : requisitosCursoRepository.findActivosPorPlanesYCursos(idsPlan, Set.of(nivel.getId()))) {
            if (!requisito.getIdCurso().getId().equals(nivel.getId())
                    || !TIPO_PRERREQUISITO.equals(requisito.getIdTipoRequisito().getCodigo())) {
                continue;
            }
            AvanceInstrumentoService.CursoAprobado aprobado = aprobados.get(requisito.getIdCursoRequerido().getId());
            if (aprobado == null || !aprobado.cumple(requisito.getNotaMinima())) {
                faltantes.add(nombreCurso(requisito.getIdCursoRequerido()));
            }
        }

        return new ArrayList<>(faltantes);
    }

    /*
     * Cada espacio elegido debe ser de un instrumento vigente del
     * estudiante y de su profesor asignado en ese instrumento, uno por
     * instrumento. Devuelve la selección con el nivel calculado y el
     * instrumento que se cursa.
     *
     * instrumentoPedido: idEspacio -> instrumento de la lección para la que
     * se eligió (puede faltar).
     */
    private List<Seleccion> asignarNiveles(
            List<Long> idsEspacio,
            Map<Long, Integer> instrumentoPedido,
            Map<Long, Grupo> gruposPorId,
            List<Leccion> lecciones,
            Set<String> errores) {

        List<Seleccion> resultado = new ArrayList<>();
        Set<Integer> instrumentosElegidos = new HashSet<>();

        for (Long idEspacio : idsEspacio) {

            Grupo espacio = gruposPorId.get(idEspacio);

            if (!espacio.esEspacioInstrumento()) {
                errores.add("El grupo " + espacio.getCodigo() + " no es un espacio de instrumento.");
                continue;
            }

            Integer pedido = instrumentoPedido.get(idEspacio);

            Leccion leccion = espacio.getIdInstrumento() != null
                    ? leccionDeEspacioFijo(espacio, pedido, lecciones, errores)
                    : leccionDeEspacioVarios(espacio, pedido, lecciones, errores);

            if (leccion == null) {
                continue;
            }

            String instrumento = leccion.nombreInstrumento();

            if (leccion.profesor() == null || !Objects.equals(leccion.profesor().getId(), espacio.getIdProfesor())) {
                errores.add("El espacio " + espacio.getCodigo() + " no es de su profesor asignado en "
                        + instrumento + ".");
                continue;
            }

            if (leccion.matriculada() != null || !instrumentosElegidos.add(leccion.idInstrumento())) {
                errores.add("Solo puede tener un espacio de " + instrumento + " por período.");
                continue;
            }

            if (leccion.nivel() == null) {
                errores.add("Ya completó todos los niveles de " + instrumento + ".");
                continue;
            }

            resultado.add(new Seleccion(espacio, leccion.nivel().getId(), leccion.idInstrumento()));
        }

        return resultado;
    }

    // Espacio de un solo instrumento: la lección es la de ese instrumento.
    private Leccion leccionDeEspacioFijo(Grupo espacio, Integer pedido, List<Leccion> lecciones, Set<String> errores) {

        if (pedido != null && !pedido.equals(espacio.getIdInstrumento())) {
            errores.add("El espacio " + espacio.getCodigo() + " no es del instrumento indicado.");
            return null;
        }

        Leccion leccion = lecciones.stream()
                .filter(l -> Objects.equals(l.idInstrumento(), espacio.getIdInstrumento()))
                .findFirst()
                .orElse(null);

        if (leccion == null) {
            errores.add("El espacio " + espacio.getCodigo()
                    + " es de un instrumento que usted no tiene asignado.");
        }
        return leccion;
    }

    /*
     * Espacio de "cualquier instrumento del profesor": sirve a las lecciones
     * con ese profesor en un instrumento que él da. Si viene la lección
     * (pedido) se usa esa; si no, se deduce cuando es una sola.
     */
    private Leccion leccionDeEspacioVarios(Grupo espacio, Integer pedido, List<Leccion> lecciones, Set<String> errores) {

        if (pedido != null) {
            Leccion leccion = lecciones.stream()
                    .filter(l -> Objects.equals(l.idInstrumento(), pedido))
                    .findFirst()
                    .orElse(null);

            if (leccion == null) {
                errores.add("El espacio " + espacio.getCodigo()
                        + " se eligió para un instrumento que usted no tiene asignado.");
                return null;
            }
            if (leccion.profesor() != null && Objects.equals(leccion.profesor().getId(), espacio.getIdProfesor())
                    && !EspecialidadProfesor.da(leccion.profesor(), leccion.asignacion().getInstrumento())) {
                errores.add("El espacio " + espacio.getCodigo() + " no incluye " + leccion.nombreInstrumento() + ".");
                return null;
            }
            // Si el profesor no es el asignado, lo informa asignarNiveles.
            return leccion;
        }

        List<Leccion> candidatas = lecciones.stream()
                .filter(l -> l.profesor() != null && Objects.equals(l.profesor().getId(), espacio.getIdProfesor()))
                .filter(l -> EspecialidadProfesor.da(l.profesor(), l.asignacion().getInstrumento()))
                .toList();

        if (candidatas.size() == 1) {
            return candidatas.get(0);
        }

        if (candidatas.isEmpty()) {
            errores.add("El espacio " + espacio.getCodigo()
                    + " no es de su profesor asignado en ninguno de sus instrumentos.");
        } else {
            errores.add("Indique para cuál lección es el espacio " + espacio.getCodigo() + " ("
                    + candidatas.stream().map(Leccion::nombreInstrumento).collect(Collectors.joining(" o "))
                    + ").");
        }
        return null;
    }

    // Por cada instrumento con nivel pendiente debe venir un espacio.
    private void validarLeccionesCompletas(List<Leccion> lecciones, List<Seleccion> seleccionadas, Set<String> errores) {

        for (Leccion leccion : lecciones) {

            if (!leccion.requiereEspacio()) {
                continue;
            }

            boolean elegido = seleccionadas.stream()
                    .anyMatch(s -> s.esEspacio() && Objects.equals(s.idInstrumento(), leccion.idInstrumento()));

            if (elegido) {
                continue;
            }

            if (leccion.profesor() == null) {
                errores.add("No tiene profesor asignado en " + leccion.nombreInstrumento()
                        + ". Comuníquese con la administración de ACUSAB.");
            } else {
                errores.add("Elija el espacio de su lección de " + leccion.nombreInstrumento() + ".");
            }
        }
    }

    private OfertaMatriculaResponseDTO.LeccionInstrumentoDTO mapearLeccion(
            Leccion leccion,
            PeriodoAcademico periodo,
            Map<Long, MallaCurricular> mallaPorCurso,
            Map<Long, PlanEstudio> planes
    ) {

        OfertaMatriculaResponseDTO.LeccionInstrumentoDTO dto = new OfertaMatriculaResponseDTO.LeccionInstrumentoDTO();

        dto.setIdEstudianteInstrumento(leccion.asignacion().getId());
        dto.setIdInstrumento(leccion.idInstrumento());
        dto.setNombreInstrumento(leccion.nombreInstrumento());
        dto.setDelPlan(Boolean.TRUE.equals(leccion.asignacion().getEsPrincipal()));
        dto.setMotivoNivel(leccion.motivoNivel());
        dto.setCompletado(leccion.completado());
        dto.setRequerida(leccion.requiereEspacio());

        Profesor profesor = leccion.profesor();
        if (profesor != null) {
            dto.setIdProfesor(profesor.getId());
            dto.setProfesor(nombreCompleto(profesor.getNombres(), profesor.getPrimerApellido(), profesor.getSegundoApellido()));
        }

        if (leccion.nivel() != null) {
            dto.setIdCursoNivel(leccion.nivel().getId());
            dto.setCodigoNivel(leccion.nivel().getCodigo());
            dto.setNombreNivel(leccion.nivel().getNombre());
        }

        if (leccion.matriculada() != null) {
            dto.setMatriculado(mapearSelecciones(List.of(leccion.matriculada()), mallaPorCurso, planes).get(0));
            dto.setDisponible(true);
            return dto;
        }

        if (leccion.nivel() == null) {
            dto.setDisponible(false);
            dto.setMotivoNoDisponible(leccion.motivoNivel());
            return dto;
        }

        if (profesor == null) {
            dto.setDisponible(false);
            dto.setMotivoNoDisponible("No tiene profesor asignado en " + leccion.nombreInstrumento()
                    + ". Comuníquese con la administración de ACUSAB.");
            return dto;
        }

        if (!leccion.prerrequisitosIncumplidos().isEmpty()) {
            dto.setDisponible(false);
            dto.setMotivoNoDisponible("Para llevar " + leccion.nivel().getNombre() + " debe haber aprobado "
                    + String.join(", ", leccion.prerrequisitosIncumplidos()) + ".");
            return dto;
        }

        /*
         * Espacios libres de su profesor en ese instrumento, más los de
         * "cualquier instrumento del profesor" si el profesor da este
         * instrumento. Cada uno se muestra con el instrumento de la lección.
         */
        boolean daElInstrumento = EspecialidadProfesor.da(profesor, leccion.asignacion().getInstrumento());

        List<Grupo> espacios = grupoRepository
                .findEspacios(periodo.getId(), leccion.idInstrumento(), profesor.getId())
                .stream()
                .filter(g -> Objects.equals(g.getIdProfesor(), profesor.getId()))
                .filter(g -> g.getIdInstrumento() != null
                        ? g.getIdInstrumento().equals(leccion.idInstrumento())
                        : daElInstrumento)
                .filter(g -> ESTADOS_GRUPO_OFERTADOS.contains(g.getEstado()))
                .toList();

        Map<Long, Long> ocupados = contarCuposOcupados(espacios.stream().map(Grupo::getId).toList());

        List<Seleccion> libres = espacios.stream()
                .filter(g -> ocupados.getOrDefault(g.getId(), 0L) < g.getCupoMaximo())
                .map(g -> new Seleccion(g, leccion.nivel().getId(), leccion.idInstrumento()))
                .toList();

        List<OfertaMatriculaResponseDTO.GrupoDTO> libresDto =
                new ArrayList<>(mapearSelecciones(libres, mallaPorCurso, planes));

        libresDto.sort(Comparator
                .comparing((OfertaMatriculaResponseDTO.GrupoDTO g) ->
                        g.getHorarios().isEmpty() ? 8 : g.getHorarios().get(0).getDiaSemana())
                .thenComparing(g -> g.getHorarios().isEmpty() ? "" : g.getHorarios().get(0).getHoraInicio()));

        dto.setEspaciosLibres(libresDto);
        dto.setDisponible(!libresDto.isEmpty());

        if (libresDto.isEmpty()) {
            dto.setMotivoNoDisponible("Su profesor no tiene espacios libres en este período. "
                    + "Comuníquese con la administración de ACUSAB.");
        }

        return dto;
    }

    /*
     * ============================================================
     * Agrupaciones
     * ============================================================
     */

    /*
     * Lo que hace falta para decidir si el estudiante puede llevar cada
     * agrupación: su historial (avance por agrupación), sus instrumentos
     * vigentes, los instrumentos permitidos de cada agrupación, su edad al
     * inicio del período (D7) y si es de inclusión (D6, se consulta solo si
     * hace falta).
     */
    private final class DatosAgrupaciones {

        private final AvanceAgrupacionService.Historial historial;
        private final Set<Integer> instrumentosEstudiante;
        private final Map<Long, List<Instrumento>> permitidos;
        private final Integer edad;
        private final Long idEstudiante;
        private Boolean inclusion;

        DatosAgrupaciones(Estudiante estudiante, PeriodoAcademico periodo, Collection<Long> idsAgrupacion) {
            this.idEstudiante = estudiante.getId();
            this.historial = avanceAgrupacionService.cargarHistorial(estudiante.getId());
            this.instrumentosEstudiante = estudianteInstrumentoRepository
                    .findByIdEstudiante_IdAndFechaFinIsNull(estudiante.getId())
                    .stream()
                    .map(e -> e.getInstrumento().getId())
                    .collect(Collectors.toSet());
            this.permitidos = agrupacionService.instrumentosPermitidos(idsAgrupacion);
            this.edad = ReglasEdad.edadAl(estudiante.getFechaNacimiento(), periodo.getFechaInicio());
        }

        AvanceAgrupacionService.Historial historial() {
            return historial;
        }

        boolean esInclusion() {
            if (inclusion == null) {
                inclusion = estudianteRepository.findCodigoTipoEstudiante(idEstudiante)
                        .map(ReglasAgrupaciones.TIPO_ESTUDIANTE_INCLUSION::equalsIgnoreCase)
                        .orElse(false);
            }
            return inclusion;
        }
    }

    /*
     * Por qué el estudiante no puede elegir la agrupación (null = puede).
     * El cupo se revisa aparte. Nunca menciona el nivel.
     */
    private String motivoBloqueoAgrupacion(Agrupacion agrupacion, DatosAgrupaciones datos) {

        // D6: solo inclusión.
        if (Boolean.TRUE.equals(agrupacion.getSoloInclusion()) && !datos.esInclusion()) {
            return "Es solo para estudiantes de inclusión.";
        }

        // D7: edad al inicio del período.
        String edad = ReglasEdad.motivoBloqueo(agrupacion.getEdadMinima(), agrupacion.getEdadMaxima(), datos.edad);
        if (edad != null) {
            return edad;
        }

        List<Instrumento> permitidos = datos.permitidos.getOrDefault(agrupacion.getId(), List.of());

        if (!permitidos.isEmpty()
                && permitidos.stream().noneMatch(i -> datos.instrumentosEstudiante.contains(i.getId()))) {
            return "Es solo para estudiantes de " + unirConO(permitidos.stream().map(Instrumento::getNombre).toList()) + ".";
        }

        AvanceAgrupacionService.SiguienteNivel nivel = avanceAgrupacionService
                .siguienteNivel(datos.historial(), agrupacion, FamiliaAgrupacion.de(agrupacion));

        if (nivel.completado() && ReglasAgrupaciones.BLOQUEAR_AGRUPACION_COMPLETADA) {
            return "Ya completó esta agrupación.";
        }

        if (!nivel.disponible()) {
            return "Esta agrupación no está disponible en este momento. Comuníquese con la dirección académica.";
        }

        return null;
    }

    /*
     * Cada grupo de agrupación elegido: publicado, de una agrupación activa,
     * sin repetir la agrupación en el período, permitido para el estudiante
     * (D6, D7 e instrumentos) y sin haberla completado (D3). Llegan en el
     * orden en que se marcaron: ese orden se guarda en orden_seleccion,
     * después de las que ya tenía matriculadas. Devuelve la selección con el
     * nivel calculado (D1 y D2) y aplica la regla del coro (D4).
     */
    private List<Seleccion> asignarNivelesAgrupacion(
            Estudiante estudiante,
            PeriodoAcademico periodo,
            List<Long> idsAgrupacion,
            Map<Long, Grupo> gruposPorId,
            List<Seleccion> yaMatriculados,
            Set<String> errores
    ) {

        if (idsAgrupacion.isEmpty()) {
            return List.of();
        }

        List<Seleccion> yaAgrupaciones = ordenarPorSeleccion(
                yaMatriculados.stream().filter(Seleccion::esAgrupacion).toList());

        List<Grupo> grupos = new ArrayList<>();
        for (Long id : idsAgrupacion) {
            Grupo grupo = gruposPorId.get(id);
            if (!grupo.esAgrupacion()) {
                errores.add("El grupo " + grupo.getCodigo() + " no es una agrupación.");
                continue;
            }
            grupos.add(grupo);
        }

        Map<Long, Agrupacion> agrupaciones = agrupacionService.agrupacionesPorId(
                grupos.stream().map(Grupo::getIdAgrupacion).toList());

        DatosAgrupaciones datos = new DatosAgrupaciones(estudiante, periodo, agrupaciones.keySet());

        Set<Long> yaEnPeriodo = yaAgrupaciones.stream().map(s -> s.grupo().getIdAgrupacion()).collect(Collectors.toSet());
        Set<Long> vistas = new HashSet<>();
        List<Seleccion> resultado = new ArrayList<>();

        // Las nuevas siguen la numeración de las que ya tenía.
        int orden = yaAgrupaciones.stream()
                .map(Seleccion::orden)
                .filter(Objects::nonNull)
                .mapToInt(Short::intValue)
                .max()
                .orElse(yaAgrupaciones.size());

        for (Grupo grupo : grupos) {

            Agrupacion agrupacion = agrupaciones.get(grupo.getIdAgrupacion());
            String nombre = agrupacion != null ? "«" + agrupacion.getNombre() + "»" : "La agrupación " + grupo.getCodigo();

            if (agrupacion == null || !Boolean.TRUE.equals(agrupacion.getActiva())) {
                errores.add(nombre + " no está disponible para matrícula.");
                continue;
            }
            if (!ReglasAgrupaciones.ESTADO_GRUPO_OFERTADO.equals(grupo.getEstado())) {
                errores.add(nombre + " no está publicada para la matrícula.");
                continue;
            }
            if (yaEnPeriodo.contains(agrupacion.getId())) {
                errores.add("Ya está matriculado en " + nombre + " durante este período.");
                continue;
            }
            if (!vistas.add(agrupacion.getId())) {
                errores.add("Eligió " + nombre + " más de una vez. Cada agrupación se matricula una sola vez por período.");
                continue;
            }

            String bloqueo = motivoBloqueoAgrupacion(agrupacion, datos);
            if (bloqueo != null) {
                errores.add(nombre + ": " + Character.toLowerCase(bloqueo.charAt(0)) + bloqueo.substring(1));
                continue;
            }

            AvanceAgrupacionService.SiguienteNivel nivel = avanceAgrupacionService
                    .siguienteNivel(datos.historial(), agrupacion, FamiliaAgrupacion.de(agrupacion));

            orden++;
            resultado.add(new Seleccion(grupo, nivel.curso().getId(), null, agrupacion, (short) orden));
        }

        aplicarReglaCoro(resultado, yaAgrupaciones, datos.historial());

        return resultado;
    }

    /*
     * D4 (automática; el estudiante no elige ni la ve): si en el período
     * lleva DOS o más agrupaciones y NINGUNA es coral, la SEGUNDA que eligió
     * (orden_seleccion) se le califica como coro: su detalle guarda el
     * siguiente nivel de Coro en ESA agrupación (D1 a D3). Con una sola
     * agrupación no aplica.
     *
     * Casos de borde:
     * - Si la segunda ya estaba matriculada de antes, no se cambia.
     * - Si la segunda ya completó Coro en esa agrupación, pasa a la tercera
     *   (y así), siempre entre las que se matriculan ahora.
     */
    private void aplicarReglaCoro(
            List<Seleccion> nuevas,
            List<Seleccion> yaMatriculadas,
            AvanceAgrupacionService.Historial historial
    ) {

        int total = yaMatriculadas.size() + nuevas.size();

        boolean aplica = ReglasAgrupaciones.CALIFICAR_INSTRUMENTAL_COMO_CORO
                && total >= ReglasAgrupaciones.AGRUPACIONES_PARA_CORO
                && nuevas.stream().noneMatch(Seleccion::esCoral)
                && yaMatriculadas.stream().noneMatch(Seleccion::esCoral)
                && !yaLlevaCoro(yaMatriculadas, historial);

        if (!aplica) {
            return;
        }

        // Posición 1 = la segunda elegida en el período.
        for (int posicion = ReglasAgrupaciones.AGRUPACIONES_PARA_CORO - 1; posicion < total; posicion++) {
            int indice = posicion - yaMatriculadas.size();
            if (indice < 0) {
                continue;
            }
            Seleccion seleccion = nuevas.get(indice);
            AvanceAgrupacionService.SiguienteNivel coro =
                    avanceAgrupacionService.siguienteNivel(historial, seleccion.agrupacion(), FamiliaAgrupacion.CORO);
            if (coro.disponible()) {
                nuevas.set(indice, new Seleccion(seleccion.grupo(), coro.curso().getId(), null, seleccion.agrupacion(), seleccion.orden()));
                return;
            }
        }
    }

    // Ya tiene una coral en el período o una instrumental calificada como coro.
    private boolean yaLlevaCoro(List<Seleccion> agrupacionesMatriculadas, AvanceAgrupacionService.Historial historial) {
        return agrupacionesMatriculadas.stream().anyMatch(s -> s.esCoral()
                || (s.esInstrumental() && historial.familiaDe(s.idCurso()) == FamiliaAgrupacion.CORO));
    }

    // Por orden de selección (las viejas sin orden, al final y por id de grupo).
    private static List<Seleccion> ordenarPorSeleccion(List<Seleccion> agrupaciones) {
        return agrupaciones.stream()
                .sorted(Comparator
                        .comparing(Seleccion::orden, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(s -> s.grupo().getId()))
                .toList();
    }

    private List<OfertaMatriculaResponseDTO.HorarioDTO> mapearHorarios(List<HorarioGrupo> horarios) {
        List<OfertaMatriculaResponseDTO.HorarioDTO> resultado = new ArrayList<>();
        for (HorarioGrupo horario : horarios) {
            OfertaMatriculaResponseDTO.HorarioDTO h = new OfertaMatriculaResponseDTO.HorarioDTO();
            h.setDiaSemana(horario.getDiaSemana());
            h.setHoraInicio(horario.getHoraInicio().format(FORMATO_HORA));
            h.setHoraFin(horario.getHoraFin().format(FORMATO_HORA));
            h.setAula(horario.getAula());
            resultado.add(h);
        }
        return resultado;
    }

    // ["Violín", "Viola", "Violonchelo"] -> "Violín, Viola o Violonchelo"
    private static String unirConO(List<String> nombres) {
        if (nombres.size() <= 1) {
            return String.join("", nombres);
        }
        return String.join(", ", nombres.subList(0, nombres.size() - 1)) + " o " + nombres.get(nombres.size() - 1);
    }

    /*
     * ============================================================
     * Validaciones de la selección
     * ============================================================
     *
     * Devuelve todos los motivos por los que la selección no puede
     * registrarse (lista vacía = es válida).
     */
    private List<String> validarSeleccion(
            Estudiante estudiante,
            PeriodoAcademico periodo,
            List<Long> idsPlan,
            Map<Long, MallaCurricular> mallaPorCurso,
            List<Seleccion> seleccionados,
            List<Seleccion> yaMatriculados
    ) {

        Set<String> errores = new LinkedHashSet<>();

        Set<Long> idsCurso = new HashSet<>();
        seleccionados.forEach(s -> idsCurso.add(s.idCurso()));
        yaMatriculados.forEach(s -> idsCurso.add(s.idCurso()));
        idsCurso.remove(null);

        // Para los mensajes de error, incluye el nombre de los requeridos.
        List<RequisitosCurso> requisitos = requisitosCursoRepository.findActivosPorPlanesYCursos(idsPlan, idsCurso);

        requisitos.forEach(requisito -> idsCurso.add(requisito.getIdCursoRequerido().getId()));

        Map<Long, Curso> cursos = cursoRepository.findAllById(idsCurso)
                .stream()
                .collect(Collectors.toMap(Curso::getId, curso -> curso, (a, b) -> a));

        Map<Integer, String> instrumentos = nombresInstrumento(Stream.concat(seleccionados.stream(), yaMatriculados.stream())
                .filter(Seleccion::esEspacio)
                .map(Seleccion::idInstrumento)
                .collect(Collectors.toSet()));

        Set<String> clavesMatriculadas = yaMatriculados.stream().map(Seleccion::clave).collect(Collectors.toSet());

        Map<Long, Long> ocupados = contarCuposOcupados(seleccionados.stream().map(s -> s.grupo().getId()).toList());

        Set<String> clavesVistas = new HashSet<>();

        for (Seleccion seleccion : seleccionados) {

            Grupo grupo = seleccion.grupo();
            Curso curso = cursos.get(seleccion.idCurso());
            String etiqueta = etiqueta(seleccion, curso, instrumentos);

            if (!Objects.equals(grupo.getIdPeriodo(), periodo.getId())) {
                errores.add("El grupo " + etiqueta + " no pertenece al período vigente.");
            }

            if (!ESTADOS_GRUPO_OFERTADOS.contains(grupo.getEstado())) {
                errores.add("El grupo " + etiqueta + " no está disponible para matrícula.");
            }

            if (curso == null || !Boolean.TRUE.equals(curso.getActivo())) {
                errores.add("El curso del grupo " + etiqueta + " no se encuentra activo.");
            }

            // Instrumento I..XIV solo se matricula en espacios de instrumento.
            if (!seleccion.esEspacio() && curso != null && Boolean.TRUE.equals(curso.getEsCursoInstrumento())) {
                errores.add(nombreCurso(curso)
                        + " es un curso de instrumento: se matricula eligiendo un espacio de su profesor.");
            }

            if (!seleccion.esEspacio() && !seleccion.esAgrupacion() && curso != null) {

                // Conjunto Instrumental y Coro solo se cursan en agrupaciones.
                if (Boolean.TRUE.equals(curso.getEsCursoAgrupacion())) {
                    errores.add(nombreCurso(curso)
                            + " se cursa en una agrupación: elíjala en el paso de agrupaciones.");
                }

                // D7: edad del estudiante al inicio del período.
                String edad = ReglasEdad.motivoBloqueo(curso.getEdadMinima(), curso.getEdadMaxima(),
                        ReglasEdad.edadAl(estudiante.getFechaNacimiento(), periodo.getFechaInicio()));
                if (edad != null) {
                    errores.add(nombreCurso(curso) + ": " + Character.toLowerCase(edad.charAt(0)) + edad.substring(1));
                }
            }

            /*
             * Seguridad: solo se pueden matricular cursos de los planes
             * en los que el estudiante está inscrito. En una agrupación el
             * nivel lo calcula el sistema y puede no estar en la malla; que
             * no se repita la agrupación se revisa en asignarNivelesAgrupacion
             * (con un mensaje que no muestra el nivel).
             */
            if (!seleccion.esAgrupacion()) {

                if (!mallaPorCurso.containsKey(seleccion.idCurso())) {
                    errores.add("El curso " + etiqueta
                            + " no pertenece a ningún plan de estudio en el que usted está inscrito.");
                }

                if (!clavesVistas.add(seleccion.clave())) {
                    errores.add("Seleccionó más de un grupo del curso " + nombreCurso(curso) + ".");
                }

                if (clavesMatriculadas.contains(seleccion.clave())) {
                    errores.add("Ya está matriculado en el curso " + nombreCurso(curso) + " durante este período.");
                }
            }

            if (ocupados.getOrDefault(grupo.getId(), 0L) >= grupo.getCupoMaximo()) {
                errores.add(seleccion.esEspacio()
                        ? "El espacio " + etiqueta + " ya lo tomó otro estudiante. Elija otro espacio."
                        : "El grupo " + etiqueta + " no tiene cupos disponibles.");
            }
        }

        validarRequisitos(estudiante, requisitos, cursos, seleccionados, yaMatriculados, errores);

        validarHorarios(seleccionados, yaMatriculados, cursos, instrumentos, errores);

        return new ArrayList<>(errores);
    }

    /*
     * Prerrequisitos: el curso requerido debe estar aprobado (con la
     * nota mínima, si el plan la exige) o reconocido (cursos_reconocidos).
     *
     * Correquisitos: el curso requerido debe estar aprobado, ya
     * matriculado en el período o incluido en esta misma selección.
     *
     * En un nivel de instrumento (espacio), los cursos de instrumento
     * requeridos cuentan solo si están aprobados en ESE instrumento.
     */
    private void validarRequisitos(
            Estudiante estudiante,
            List<RequisitosCurso> requisitos,
            Map<Long, Curso> cursos,
            List<Seleccion> seleccionados,
            List<Seleccion> yaMatriculados,
            Set<String> errores
    ) {

        Set<Long> cursosSeleccionados = seleccionados.stream().map(Seleccion::idCurso).collect(Collectors.toSet());
        Set<Long> cursosMatriculados = yaMatriculados.stream().map(Seleccion::idCurso).collect(Collectors.toSet());

        Map<Long, AvanceInstrumentoService.CursoAprobado> aprobadosGenerales = null;
        Map<Integer, Map<Long, AvanceInstrumentoService.CursoAprobado>> aprobadosPorInstrumento = new HashMap<>();

        for (Seleccion seleccion : seleccionados) {

            /*
             * Agrupación: no se aplica la validación general de requisitos
             * del curso. El nivel ya viene de D1 a D3 (lo aprobado en ESA
             * agrupación o el historial sin agrupación).
             */
            if (seleccion.esAgrupacion()) {
                continue;
            }

            for (RequisitosCurso requisito : requisitos) {

                Long idCurso = requisito.getIdCurso().getId();

                if (!idCurso.equals(seleccion.idCurso())) {
                    continue;
                }

                Map<Long, AvanceInstrumentoService.CursoAprobado> aprobados;
                if (seleccion.esEspacio()) {
                    aprobados = aprobadosPorInstrumento.computeIfAbsent(seleccion.idInstrumento(),
                            id -> aprobadosParaInstrumento(estudiante.getId(), id));
                } else {
                    if (aprobadosGenerales == null) {
                        aprobadosGenerales = aprobadosDelPlan(estudiante.getId());
                    }
                    aprobados = aprobadosGenerales;
                }

                Long idRequerido = requisito.getIdCursoRequerido().getId();
                String nombreCurso = nombreCurso(cursos.get(idCurso));
                String nombreRequerido = nombreCurso(cursos.get(idRequerido));
                String codigoTipo = requisito.getIdTipoRequisito().getCodigo();

                AvanceInstrumentoService.CursoAprobado aprobado = aprobados.get(idRequerido);
                boolean estaAprobado = aprobado != null && aprobado.cumple(requisito.getNotaMinima());

                if (TIPO_PRERREQUISITO.equals(codigoTipo)) {

                    if (!estaAprobado) {
                        errores.add("Prerrequisito incumplido en " + nombreCurso
                                + ": debe haber aprobado " + nombreRequerido + ".");
                    }

                } else if (TIPO_CORREQUISITO.equals(codigoTipo)) {

                    boolean cumplido = estaAprobado
                            || cursosMatriculados.contains(idRequerido)
                            || cursosSeleccionados.contains(idRequerido);

                    if (!cumplido) {
                        errores.add("Correquisito incumplido en " + nombreCurso
                                + ": debe matricularse junto con " + nombreRequerido + ".");
                    }
                }
            }
        }
    }

    /*
     * No puede haber conflictos de horario entre los grupos y espacios
     * seleccionados ni con los que el estudiante ya tiene matriculados en
     * el período (también entre lecciones de dos instrumentos).
     */
    private void validarHorarios(
            List<Seleccion> seleccionados,
            List<Seleccion> yaMatriculados,
            Map<Long, Curso> cursos,
            Map<Integer, String> instrumentos,
            Set<String> errores
    ) {

        List<Long> idsGrupo = new ArrayList<>();
        seleccionados.forEach(s -> idsGrupo.add(s.grupo().getId()));
        yaMatriculados.forEach(s -> idsGrupo.add(s.grupo().getId()));

        Map<Long, List<HorarioGrupo>> horarios = horariosPorGrupo(idsGrupo);

        for (int i = 0; i < seleccionados.size(); i++) {

            Seleccion actual = seleccionados.get(i);
            String etiqueta = etiqueta(actual, cursos.get(actual.idCurso()), instrumentos);

            for (int j = i + 1; j < seleccionados.size(); j++) {

                Seleccion otro = seleccionados.get(j);

                if (hayChoque(horarios.get(actual.grupo().getId()), horarios.get(otro.grupo().getId()))) {
                    errores.add("Choque de horario entre " + etiqueta + " y "
                            + etiqueta(otro, cursos.get(otro.idCurso()), instrumentos) + ".");
                }
            }

            for (Seleccion matriculado : yaMatriculados) {

                if (hayChoque(horarios.get(actual.grupo().getId()), horarios.get(matriculado.grupo().getId()))) {
                    errores.add("Choque de horario entre " + etiqueta + " y "
                            + etiqueta(matriculado, cursos.get(matriculado.idCurso()), instrumentos)
                            + ", que ya tiene matriculado en este período.");
                }
            }
        }
    }

    private boolean hayChoque(List<HorarioGrupo> a, List<HorarioGrupo> b) {

        if (a == null || b == null) {
            return false;
        }

        return a.stream().anyMatch(ha ->
                b.stream().anyMatch(hb ->
                        ha.getDiaSemana().equals(hb.getDiaSemana())
                                && ha.getHoraInicio().isBefore(hb.getHoraFin())
                                && hb.getHoraInicio().isBefore(ha.getHoraFin())));
    }

    /*
     * La base de datos valida los correquisitos al insertar cada curso
     * (trg_matriculas_detalles_bi_validar_requisitos): el curso requerido
     * debe estar ya en la matrícula o aprobado. Por eso se insertan primero
     * los cursos de los que otros de la selección dependen, sin alterar
     * el resto del orden en que el estudiante los eligió.
     */
    private List<Seleccion> ordenarCorrequisitosPrimero(List<Seleccion> selecciones, List<Long> idsPlan) {

        Set<Long> idsCurso = selecciones.stream().map(Seleccion::idCurso).collect(Collectors.toSet());

        // curso -> cursos de la selección que debe llevar junto con él.
        Map<Long, Set<Long>> dependencias = new HashMap<>();

        requisitosCursoRepository
                .findActivosPorPlanesYCursos(idsPlan, idsCurso)
                .stream()
                .filter(requisito -> TIPO_CORREQUISITO.equals(requisito.getIdTipoRequisito().getCodigo()))
                .filter(requisito -> idsCurso.contains(requisito.getIdCursoRequerido().getId()))
                .forEach(requisito -> dependencias
                        .computeIfAbsent(requisito.getIdCurso().getId(), id -> new HashSet<>())
                        .add(requisito.getIdCursoRequerido().getId()));

        List<Seleccion> pendientes = new ArrayList<>(selecciones);
        List<Seleccion> ordenados = new ArrayList<>();
        Set<Long> cursosInsertados = new HashSet<>();

        while (!pendientes.isEmpty()) {

            // Si dos cursos se exigen mutuamente no existe un orden posible:
            // se sigue el orden original. Funciona porque, al insertar, se
            // marca @sigac_correquisitos_validados y el trigger omite esa
            // revisión. El orden se conserva por si la base de datos aún
            // no tiene la versión del trigger que entiende la marca.
            Seleccion siguiente = pendientes.stream()
                    .filter(s -> cursosInsertados.containsAll(dependencias.getOrDefault(s.idCurso(), Set.of())))
                    .findFirst()
                    .orElse(pendientes.get(0));

            pendientes.remove(siguiente);
            ordenados.add(siguiente);
            cursosInsertados.add(siguiente.idCurso());
        }

        return ordenados;
    }

    /*
     * Los triggers de la base de datos rechazan con SQLSTATE 45000 y un
     * mensaje propio (por ejemplo, "El estudiante no cumple los
     * prerrequisitos del curso"). Devuelve ese mensaje, o null si el error
     * es de otra naturaleza.
     */
    private String mensajeDeReglaDeBaseDeDatos(Throwable error) {

        for (Throwable causa = error; causa != null; causa = causa.getCause()) {

            if (causa instanceof SQLException sqlException && "45000".equals(sqlException.getSQLState())) {
                return sqlException.getMessage();
            }
        }

        return null;
    }

    /*
     * ============================================================
     * Registro de la matrícula
     * ============================================================
     *
     * Solo existe una matrícula por autorización. Si otra confirmación
     * simultánea la creó primero, se informa al estudiante en lugar de
     * fallar con un error interno.
     */
    private Matricula crearMatricula(AutorizacionesMatricula autorizacion, Long idPlanEstudio) {

        EstadoMatricula registrada = estadoMatriculaRepository
                .findByCodigoAndActivoTrue(ESTADO_MATRICULA_REGISTRADA)
                .orElseThrow(() -> new BusinessException("No se encontró el estado de matrícula requerido."));

        Matricula matricula = new Matricula();
        matricula.setAutorizacion(autorizacion);
        matricula.setIdPlanEstudio(idPlanEstudio);
        matricula.setEstado(registrada);

        try {
            return matriculaRepository.saveAndFlush(matricula);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("No fue posible registrar la matrícula. Intente nuevamente.");
        }
    }

    /*
     * ============================================================
     * Habilitación
     * ============================================================
     *
     * Devuelve null cuando el estudiante está AUTORIZADO para el
     * período; de lo contrario, el motivo que se le muestra.
     */
    private String motivoSinAutorizacion(Optional<AutorizacionesMatricula> autorizacion) {

        if (autorizacion.isEmpty()) {
            return "El estudiante no está habilitado para matricular en este período. "
                    + "Requiere la revisión previa del administrador académico.";
        }

        String codigo = autorizacion.get().getIdEstadoAutorizacion().getCodigo();

        if (ESTADO_AUTORIZACION_AUTORIZADA.equalsIgnoreCase(codigo)) {
            return null;
        }

        if (ESTADO_AUTORIZACION_PENDIENTE.equalsIgnoreCase(codigo)) {
            return "Su solicitud de matrícula está pendiente de revisión por el administrador académico. "
                    + "Podrá matricular cuando sea autorizada.";
        }

        if (ESTADO_AUTORIZACION_RECHAZADA.equalsIgnoreCase(codigo)) {
            return "El administrador académico no autorizó su matrícula para este período. "
                    + "Consulte sus notificaciones para conocer el motivo.";
        }

        return "Su autorización de matrícula para este período fue anulada. "
                + "Requiere una nueva revisión del administrador académico.";
    }

    private OfertaMatriculaResponseDTO noHabilitado(OfertaMatriculaResponseDTO respuesta, String motivo) {
        respuesta.setHabilitado(false);
        respuesta.setMotivoNoHabilitado(motivo);
        return respuesta;
    }

    /*
     * ============================================================
     * Consultas de apoyo
     * ============================================================
     */

    /*
     * Período vigente: el que se encuentra en etapa de matrícula. Si
     * hubiera más de uno, se toma el que inició más recientemente.
     */
    private Optional<PeriodoAcademico> buscarPeriodoVigente() {
        return periodoAcademicoRepository
                .findByEstadoInOrderByFechaInicioDesc(List.of(ESTADO_PERIODO_MATRICULA))
                .stream()
                .findFirst();
    }

    private List<Long> idsPlanesInscritos(Long idEstudiante) {
        return estudiantePlanRepository
                .findByIdEstudianteAndActivoTrue(idEstudiante)
                .stream()
                .map(EstudiantePlanEstudio::getIdPlanEstudio)
                .distinct()
                .toList();
    }

    private Map<Long, PlanEstudio> planesPorId(List<Long> idsPlan) {
        return planEstudioRepository
                .findAllById(idsPlan)
                .stream()
                .collect(Collectors.toMap(PlanEstudio::getId, plan -> plan, (a, b) -> a));
    }

    /*
     * Cursos disponibles para el estudiante: los de la malla de los
     * planes en los que está inscrito. Si un curso está en varios de
     * esos planes se toma el de menor id, de forma determinista.
     */
    private Map<Long, MallaCurricular> mallaPorCurso(Collection<Long> idsPlan) {

        Map<Long, MallaCurricular> resultado = new HashMap<>();

        mallaRepository
                .findByIdPlanEstudioInAndActivoTrue(idsPlan)
                .stream()
                .sorted(Comparator.comparing(MallaCurricular::getIdPlanEstudio))
                .forEach(malla -> resultado.putIfAbsent(malla.getIdCurso(), malla));

        return resultado;
    }

    private Set<Long> cursosDeInstrumento(Collection<Long> idsCurso) {
        if (idsCurso.isEmpty()) {
            return Set.of();
        }
        return cursoRepository.findAllById(idsCurso)
                .stream()
                .filter(c -> Boolean.TRUE.equals(c.getEsCursoInstrumento())
                        || Boolean.TRUE.equals(c.getEsCursoAgrupacion()))
                .map(Curso::getId)
                .collect(Collectors.toSet());
    }

    /*
     * Cursos aprobados para el plan: los de AvanceInstrumentoService más los
     * de las familias de agrupación (Conjunto Instrumental y Coro) según D5:
     * cuenta la agrupación más adelantada, sin sumar dos agrupaciones.
     */
    private Map<Long, AvanceInstrumentoService.CursoAprobado> aprobadosDelPlan(Long idEstudiante) {
        Map<Long, AvanceInstrumentoService.CursoAprobado> resultado =
                new HashMap<>(avanceInstrumentoService.cursosAprobados(idEstudiante));
        avanceAgrupacionService.cursosAprobadosDelPlan(idEstudiante).forEach(resultado::putIfAbsent);
        return resultado;
    }

    private Map<Long, AvanceInstrumentoService.CursoAprobado> aprobadosParaInstrumento(Long idEstudiante, Integer idInstrumento) {
        Map<Long, AvanceInstrumentoService.CursoAprobado> resultado =
                new HashMap<>(avanceInstrumentoService.cursosAprobadosParaInstrumento(idEstudiante, idInstrumento));
        avanceAgrupacionService.cursosAprobadosDelPlan(idEstudiante).forEach(resultado::putIfAbsent);
        return resultado;
    }

    /*
     * Grupos y espacios que el estudiante ya tiene matriculados (no
     * retirados) en el período de la autorización.
     */
    private List<Seleccion> seleccionesYaMatriculadas(AutorizacionesMatricula autorizacion) {
        return matriculaRepository
                .findByAutorizacion_Id(autorizacion.getId())
                .filter(matricula -> ESTADO_MATRICULA_REGISTRADA.equals(matricula.getEstado().getCodigo()))
                .map(this::seleccionesDeMatricula)
                .orElse(List.of());
    }

    private List<Seleccion> seleccionesDeMatricula(Matricula matricula) {

        List<MatriculaDetalle> detalles = matriculaDetalleRepository.findByMatricula_IdAndRetiradoFalse(matricula.getId());

        if (detalles.isEmpty()) {
            return List.of();
        }

        Map<Long, Grupo> grupos = grupoRepository
                .findAllById(detalles.stream().map(MatriculaDetalle::getIdGrupo).toList())
                .stream()
                .collect(Collectors.toMap(Grupo::getId, Function.identity(), (a, b) -> a));

        // Solo se consultan las agrupaciones si hay grupos de agrupación.
        List<Long> idsAgrupacion = grupos.values().stream()
                .filter(Grupo::esAgrupacion)
                .map(Grupo::getIdAgrupacion)
                .toList();
        Map<Long, Agrupacion> agrupaciones = idsAgrupacion.isEmpty()
                ? Map.of() : agrupacionService.agrupacionesPorId(idsAgrupacion);

        List<Seleccion> resultado = new ArrayList<>();

        for (MatriculaDetalle detalle : detalles) {
            Grupo grupo = grupos.get(detalle.getIdGrupo());
            if (grupo != null) {
                // En un espacio, el instrumento es el del detalle (si falta, el del espacio).
                Integer idInstrumento = !grupo.esEspacioInstrumento() ? null
                        : detalle.getIdInstrumento() != null ? detalle.getIdInstrumento() : grupo.getIdInstrumento();
                resultado.add(new Seleccion(grupo,
                        detalle.getIdCurso() != null ? detalle.getIdCurso() : grupo.getIdCurso(),
                        idInstrumento,
                        grupo.esAgrupacion() ? agrupaciones.get(grupo.getIdAgrupacion()) : null,
                        detalle.getOrdenSeleccion(),
                        detalle.getIdProfesorAsignado()));
            }
        }

        return resultado;
    }

    private Map<Long, Long> contarCuposOcupados(Collection<Long> idsGrupo) {

        if (idsGrupo.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> resultado = new HashMap<>();

        for (Object[] fila : matriculaDetalleRepository.contarCuposOcupados(idsGrupo, ESTADO_MATRICULA_REGISTRADA)) {
            resultado.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
        }

        return resultado;
    }

    private Map<Long, List<HorarioGrupo>> horariosPorGrupo(Collection<Long> idsGrupo) {

        if (idsGrupo.isEmpty()) {
            return Map.of();
        }

        return horarioGrupoRepository
                .findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(idsGrupo)
                .stream()
                .collect(Collectors.groupingBy(HorarioGrupo::getIdGrupo));
    }

    private Map<Integer, String> nombresInstrumento(Collection<Integer> ids) {
        Set<Integer> limpios = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (limpios.isEmpty()) {
            return Map.of();
        }
        return instrumentoRepository.findAllById(limpios)
                .stream()
                .collect(Collectors.toMap(Instrumento::getId, Instrumento::getNombre, (a, b) -> a));
    }

    /*
     * ============================================================
     * Identificar estudiante autenticado
     * ============================================================
     *
     * El JWT guarda la cédula como authentication.getName(). El
     * estudiante siempre sale de la sesión, nunca de la solicitud.
     */
    private Estudiante obtenerEstudianteAutenticado() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new BusinessException("No se pudo identificar al usuario autenticado.");
        }

        return estudianteRepository
                .findByUsuario_Cedula(authentication.getName())
                .orElseThrow(() -> new BusinessException(
                        "El usuario autenticado no está asociado a un estudiante."));
    }

    /*
     * ============================================================
     * Conversión a DTO
     * ============================================================
     */
    private List<OfertaMatriculaResponseDTO.GrupoDTO> mapearSelecciones(
            List<Seleccion> selecciones,
            Map<Long, MallaCurricular> mallaPorCurso,
            Map<Long, PlanEstudio> planes
    ) {

        if (selecciones.isEmpty()) {
            return List.of();
        }

        List<Long> idsGrupo = selecciones.stream().map(s -> s.grupo().getId()).toList();

        Set<Long> idsCurso = selecciones.stream()
                .map(Seleccion::idCurso)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Profesores de los grupos y los asignados por Dirección (cursos a coordinar).
        Set<Long> idsProfesor = selecciones.stream()
                .flatMap(s -> Stream.of(s.grupo().getIdProfesor(), s.idProfesorAsignado()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Curso> cursos = idsCurso.isEmpty()
                ? Map.of()
                : cursoRepository.findAllById(idsCurso)
                .stream()
                .collect(Collectors.toMap(Curso::getId, curso -> curso, (a, b) -> a));

        Map<Long, Profesor> profesores = idsProfesor.isEmpty()
                ? Map.of()
                : profesorRepository.findAllById(idsProfesor)
                .stream()
                .collect(Collectors.toMap(Profesor::getId, profesor -> profesor, (a, b) -> a));

        Map<Integer, String> instrumentos = nombresInstrumento(selecciones.stream()
                .filter(Seleccion::esEspacio)
                .map(Seleccion::idInstrumento)
                .collect(Collectors.toSet()));

        Map<Long, List<HorarioGrupo>> horarios = horariosPorGrupo(idsGrupo);

        Map<Long, Long> ocupados = contarCuposOcupados(idsGrupo);

        // Agrupaciones: varios profesores por grupo.
        List<Grupo> gruposAgrupacion = selecciones.stream()
                .filter(Seleccion::esAgrupacion)
                .map(Seleccion::grupo)
                .toList();
        Map<Long, List<String>> profesoresAgrupacion = gruposAgrupacion.isEmpty()
                ? Map.of() : agrupacionService.nombresProfesores(gruposAgrupacion);

        List<OfertaMatriculaResponseDTO.GrupoDTO> resultado = new ArrayList<>();

        for (Seleccion seleccion : selecciones) {

            Grupo grupo = seleccion.grupo();

            OfertaMatriculaResponseDTO.GrupoDTO dto = new OfertaMatriculaResponseDTO.GrupoDTO();

            dto.setIdGrupo(grupo.getId());
            dto.setCodigo(grupo.getCodigo());
            dto.setIdCurso(seleccion.idCurso());
            dto.setModalidad(grupo.getModalidad());
            dto.setAula(grupo.getAula());

            Curso curso = seleccion.idCurso() == null ? null : cursos.get(seleccion.idCurso());

            if (curso != null) {
                dto.setCodigoCurso(curso.getCodigo());
                dto.setNombreCurso(curso.getNombre());
            }

            // Un espacio se muestra con su nivel: "Instrumento VI · Piano".
            if (seleccion.esEspacio()) {
                String instrumento = instrumentos.get(seleccion.idInstrumento());
                dto.setEspacioInstrumento(true);
                dto.setIdInstrumento(seleccion.idInstrumento());
                dto.setNombreInstrumento(instrumento);
                dto.setNombreNivel(curso != null ? curso.getNombre() : null);
                dto.setNombreCurso(Stream.of(curso != null ? curso.getNombre() : null, instrumento)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(" · ")));
            }

            /*
             * Agrupación: al estudiante solo se le muestra el nombre
             * ("Orquesta"). El nivel es interno y no viaja en la respuesta.
             */
            if (seleccion.esAgrupacion()) {
                Agrupacion agrupacion = seleccion.agrupacion();
                dto.setAgrupacion(true);
                dto.setIdAgrupacion(grupo.getIdAgrupacion());
                dto.setNombreAgrupacion(seleccion.nombreAgrupacion());
                dto.setTipoAgrupacion(agrupacion != null ? agrupacion.getTipo() : null);
                dto.setIdCurso(null);
                dto.setCodigoCurso(null);
                dto.setNombreCurso(seleccion.nombreAgrupacion());
                dto.setNombreNivel(null);
                dto.setProfesores(profesoresAgrupacion.getOrDefault(grupo.getId(), List.of()));
            }

            MallaCurricular malla = seleccion.esAgrupacion() || seleccion.idCurso() == null
                    ? null : mallaPorCurso.get(seleccion.idCurso());

            if (malla != null) {

                dto.setNivel(malla.getNivel());
                dto.setIdPlanEstudio(malla.getIdPlanEstudio());

                PlanEstudio plan = planes.get(malla.getIdPlanEstudio());

                if (plan != null) {
                    dto.setNombrePlan(plan.getNombre());
                }
            }

            // Los grupos creados en HU-028 aún no tienen profesor asignado.
            Profesor profesor = grupo.getIdProfesor() == null ? null : profesores.get(grupo.getIdProfesor());

            if (!dto.getProfesores().isEmpty()) {
                dto.setProfesor(String.join(", ", dto.getProfesores()));
            } else if (profesor != null) {
                dto.setProfesor(nombreCompleto(
                        profesor.getNombres(), profesor.getPrimerApellido(), profesor.getSegundoApellido()));
            }

            /*
             * Curso a coordinar en Dirección: puede no tener horario ni
             * profesor. profesorAsignado es el que la Dirección le asignó a
             * ESTE estudiante (solo si ya está matriculado).
             */
            if (!seleccion.esEspacio() && !seleccion.esAgrupacion() && curso != null && curso.esACoordinar()) {
                dto.setACoordinar(true);
                Profesor asignado = seleccion.idProfesorAsignado() == null
                        ? null : profesores.get(seleccion.idProfesorAsignado());
                if (asignado != null) {
                    dto.setProfesorAsignado(nombreCompleto(
                            asignado.getNombres(), asignado.getPrimerApellido(), asignado.getSegundoApellido()));
                }
            }

            int cupoMaximo = grupo.getCupoMaximo();
            long ocupadosGrupo = ocupados.getOrDefault(grupo.getId(), 0L);

            dto.setCupoMaximo(cupoMaximo);
            dto.setCupoDisponible((int) Math.max(0, cupoMaximo - ocupadosGrupo));

            for (HorarioGrupo horario : horarios.getOrDefault(grupo.getId(), List.of())) {

                OfertaMatriculaResponseDTO.HorarioDTO h = new OfertaMatriculaResponseDTO.HorarioDTO();

                h.setDiaSemana(horario.getDiaSemana());
                h.setHoraInicio(horario.getHoraInicio().format(FORMATO_HORA));
                h.setHoraFin(horario.getHoraFin().format(FORMATO_HORA));
                h.setAula(horario.getAula());

                dto.getHorarios().add(h);
            }

            resultado.add(dto);
        }

        return resultado;
    }

    private Map<Long, List<OfertaMatriculaResponseDTO.RequisitoDTO>> mapearRequisitos(
            Collection<Long> idsPlan,
            Collection<Long> idsCurso
    ) {

        Map<Long, List<OfertaMatriculaResponseDTO.RequisitoDTO>> resultado = new LinkedHashMap<>();

        if (idsCurso.isEmpty()) {
            return resultado;
        }

        Set<String> vistos = new HashSet<>();

        for (RequisitosCurso requisito : requisitosCursoRepository.findActivosPorPlanesYCursos(idsPlan, idsCurso)) {

            String tipo = TIPO_PRERREQUISITO.equals(requisito.getIdTipoRequisito().getCodigo())
                    ? REQUISITO_PANTALLA
                    : CORREQUISITO_PANTALLA;

            Long idCurso = requisito.getIdCurso().getId();
            Curso requerido = requisito.getIdCursoRequerido();

            // Un mismo requisito puede repetirse en varios planes.
            if (!vistos.add(idCurso + "|" + tipo + "|" + requerido.getId())) {
                continue;
            }

            OfertaMatriculaResponseDTO.RequisitoDTO dto = new OfertaMatriculaResponseDTO.RequisitoDTO();

            dto.setTipo(tipo);
            dto.setIdCursoRequerido(requerido.getId());
            dto.setCodigoCursoRequerido(requerido.getCodigo());
            dto.setNombreCursoRequerido(requerido.getNombre());
            dto.setNotaMinima(requisito.getNotaMinima());

            resultado.computeIfAbsent(idCurso, id -> new ArrayList<>()).add(dto);
        }

        return resultado;
    }

    private OfertaMatriculaResponseDTO.EstudianteDTO mapearEstudiante(Estudiante estudiante) {

        OfertaMatriculaResponseDTO.EstudianteDTO dto = new OfertaMatriculaResponseDTO.EstudianteDTO();

        dto.setId(estudiante.getId());
        dto.setNumeroIdentificacion(estudiante.getNumeroIdentificacion());
        dto.setActivo(Boolean.TRUE.equals(estudiante.getActivo()));
        dto.setNombreCompleto(nombreCompleto(
                estudiante.getNombres(), estudiante.getPrimerApellido(), estudiante.getSegundoApellido()));

        return dto;
    }

    private OfertaMatriculaResponseDTO.PeriodoDTO mapearPeriodo(PeriodoAcademico periodo) {

        OfertaMatriculaResponseDTO.PeriodoDTO dto = new OfertaMatriculaResponseDTO.PeriodoDTO();

        dto.setId(periodo.getId());
        dto.setCodigo(periodo.getCodigo());
        dto.setNombre(periodo.getNombre());
        dto.setFechaInicioMatricula(periodo.getFechaInicioMatricula());
        dto.setFechaFinMatricula(periodo.getFechaFinMatricula());

        return dto;
    }

    private OfertaMatriculaResponseDTO.PlanDTO mapearPlan(PlanEstudio plan) {

        OfertaMatriculaResponseDTO.PlanDTO dto = new OfertaMatriculaResponseDTO.PlanDTO();

        dto.setId(plan.getId());
        dto.setCodigo(plan.getCodigo());
        dto.setNombre(plan.getNombre());

        return dto;
    }

    private String nombreCompleto(String nombres, String primerApellido, String segundoApellido) {
        return Stream.of(nombres, primerApellido, segundoApellido)
                .filter(parte -> parte != null && !parte.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }

    private String nombreCurso(Curso curso) {
        return curso == null ? "desconocido" : "«" + curso.getNombre() + "»";
    }

    // Grupo normal: «Solfeo I» (SOL-I-A). Espacio: «Instrumento VI» de Piano (ESP-PIA-3-1).
    // Agrupación: «Orquesta» (AGR-ORQ-1), sin el nivel.
    private String etiqueta(Seleccion seleccion, Curso curso, Map<Integer, String> instrumentos) {
        if (seleccion.esAgrupacion()) {
            return "«" + seleccion.nombreAgrupacion() + "» (" + seleccion.grupo().getCodigo() + ")";
        }
        String instrumento = seleccion.esEspacio() ? instrumentos.get(seleccion.idInstrumento()) : null;
        return nombreCurso(curso)
                + (instrumento != null ? " de " + instrumento : "")
                + " (" + seleccion.grupo().getCodigo() + ")";
    }
}
