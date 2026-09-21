package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.AutorizacionesMatricula;
import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.EstadoMatricula;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.EstudiantePlanEstudio;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.HorarioGrupo;
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
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.EstudiantePlanEstudioRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.HorarioGrupoRepository;
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
import cr.acusab.una.sigac.presentation.dto.response.MatriculaConfirmadaResponseDTO;
import cr.acusab.una.sigac.presentation.dto.response.OfertaMatriculaResponseDTO;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
import java.util.stream.Collectors;

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
 *    que desea matricular.
 * 3. Si todas las reglas se cumplen, la matrícula queda REGISTRADA y se
 *    descuentan los cupos, sin requerir una segunda aprobación.
 *
 * Todo se valida aquí, en el servidor. La pantalla solo replica algunas
 * reglas para dar respuesta inmediata al estudiante.
 */
@Service
public class MatriculaEstudianteService {

    private static final String ESTADO_PERIODO_MATRICULA = "MATRICULA";

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

    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("HH:mm");

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
            EstadoMatriculaRepository estadoMatriculaRepository
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

        Estudiante estudiante =
                obtenerEstudianteAutenticado();

        OfertaMatriculaResponseDTO respuesta =
                new OfertaMatriculaResponseDTO();

        respuesta.setEstudiante(
                mapearEstudiante(estudiante)
        );

        if (!Boolean.TRUE.equals(estudiante.getActivo())) {
            return noHabilitado(
                    respuesta,
                    "Solo un estudiante en estado «Activo» puede matricular cursos."
            );
        }

        Optional<PeriodoAcademico> periodoVigente =
                buscarPeriodoVigente();

        if (periodoVigente.isEmpty()) {
            return noHabilitado(
                    respuesta,
                    "No hay un período académico en etapa de matrícula actualmente."
            );
        }

        PeriodoAcademico periodo =
                periodoVigente.get();

        respuesta.setPeriodo(
                mapearPeriodo(periodo)
        );

        Optional<AutorizacionesMatricula> autorizacion =
                autorizacionRepository
                        .findByIdEstudiante_IdAndIdPeriodo_Id(
                                estudiante.getId(),
                                periodo.getId()
                        );

        String motivoAutorizacion =
                motivoSinAutorizacion(
                        autorizacion
                );

        if (motivoAutorizacion != null) {
            return noHabilitado(
                    respuesta,
                    motivoAutorizacion
            );
        }

        List<Long> idsPlan =
                idsPlanesInscritos(
                        estudiante.getId()
                );

        if (idsPlan.isEmpty()) {
            return noHabilitado(
                    respuesta,
                    "No está inscrito en ningún plan de estudio. Comuníquese con la administración."
            );
        }

        respuesta.setHabilitado(true);

        Map<Long, PlanEstudio> planes =
                planEstudioRepository
                        .findAllById(idsPlan)
                        .stream()
                        .collect(Collectors.toMap(
                                PlanEstudio::getId,
                                plan -> plan
                        ));

        planes.values()
                .stream()
                .sorted(Comparator.comparing(PlanEstudio::getNombre))
                .forEach(plan ->
                        respuesta.getPlanes().add(
                                mapearPlan(plan)
                        )
                );

        Map<Long, MallaCurricular> mallaPorCurso =
                mallaPorCurso(
                        idsPlan
                );

        List<Grupo> gruposOfertados =
                grupoRepository
                        .findByIdPeriodoAndEstadoIn(
                                periodo.getId(),
                                ESTADOS_GRUPO_OFERTADOS
                        )
                        .stream()
                        .filter(grupo ->
                                mallaPorCurso.containsKey(
                                        grupo.getIdCurso()
                                )
                        )
                        .toList();

        List<OfertaMatriculaResponseDTO.GrupoDTO> oferta =
                new ArrayList<>(
                        mapearGrupos(
                                gruposOfertados,
                                mallaPorCurso,
                                planes
                        )
                );

        oferta.sort(
                Comparator
                        .comparing(
                                OfertaMatriculaResponseDTO.GrupoDTO::getNivel,
                                Comparator.nullsLast(
                                        Comparator.naturalOrder()
                                )
                        )
                        .thenComparing(
                                OfertaMatriculaResponseDTO.GrupoDTO::getNombreCurso
                        )
                        .thenComparing(
                                OfertaMatriculaResponseDTO.GrupoDTO::getCodigo
                        )
        );

        respuesta.setOferta(oferta);

        List<Grupo> gruposMatriculados =
                gruposYaMatriculados(
                        autorizacion.orElseThrow()
                );

        respuesta.setMatriculados(
                mapearGrupos(
                        gruposMatriculados,
                        mallaPorCurso,
                        planes
                )
        );

        Set<Long> idsCursoOferta =
                gruposOfertados
                        .stream()
                        .map(Grupo::getIdCurso)
                        .collect(Collectors.toSet());

        respuesta.setRequisitosPorCurso(
                mapearRequisitos(
                        idsPlan,
                        idsCursoOferta
                )
        );

        cursosAprobados(
                estudiante.getId()
        ).forEach((idCurso, nota) -> {

            OfertaMatriculaResponseDTO.CursoAprobadoDTO aprobado =
                    new OfertaMatriculaResponseDTO.CursoAprobadoDTO();

            aprobado.setIdCurso(idCurso);
            aprobado.setNota(nota);

            respuesta.getCursosAprobados().add(aprobado);
        });

        return respuesta;
    }

    /*
     * ============================================================
     * Confirmar la selección y registrar la matrícula
     * ============================================================
     *
     * Vuelve a verificar todo dentro de la transacción, con los grupos
     * bloqueados, para que dos confirmaciones simultáneas no ocupen el
     * mismo último cupo.
     *
     * READ_COMMITTED es necesario: con el REPEATABLE READ por defecto de
     * MySQL, la transacción que espera el bloqueo seguiría viendo los
     * cupos de antes de que la otra confirmara y sobrevendería el grupo.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MatriculaConfirmadaResponseDTO confirmar(
            ConfirmarSeleccionMatriculaRequestDTO request
    ) {

        List<Long> idsGrupo =
                request == null || request.getIdsGrupo() == null
                        ? List.of()
                        : request.getIdsGrupo();

        if (idsGrupo.isEmpty()) {
            throw new BusinessException(
                    "Debe seleccionar al menos un curso para matricular."
            );
        }

        if (idsGrupo.stream().anyMatch(Objects::isNull)
                || idsGrupo.stream().distinct().count() != idsGrupo.size()) {

            throw new BusinessException(
                    "La selección contiene grupos repetidos o no válidos."
            );
        }

        Estudiante estudiante =
                obtenerEstudianteAutenticado();

        if (!Boolean.TRUE.equals(estudiante.getActivo())) {
            throw new BusinessException(
                    "Solo un estudiante en estado «Activo» puede matricular cursos."
            );
        }

        PeriodoAcademico periodo =
                buscarPeriodoVigente()
                        .orElseThrow(() ->
                                new BusinessException(
                                        "No hay un período académico en etapa de matrícula actualmente."
                                )
                        );

        /*
         * La habilitación se verifica de nuevo al confirmar: pudo
         * anularse después de que el estudiante abrió la pantalla.
         */
        Optional<AutorizacionesMatricula> autorizacionOpt =
                autorizacionRepository
                        .findByIdEstudiante_IdAndIdPeriodo_Id(
                                estudiante.getId(),
                                periodo.getId()
                        );

        String motivoAutorizacion =
                motivoSinAutorizacion(
                        autorizacionOpt
                );

        if (motivoAutorizacion != null) {
            throw new BusinessException(
                    motivoAutorizacion
            );
        }

        AutorizacionesMatricula autorizacion =
                autorizacionOpt.orElseThrow();

        List<Long> idsPlan =
                idsPlanesInscritos(
                        estudiante.getId()
                );

        if (idsPlan.isEmpty()) {
            throw new BusinessException(
                    "No está inscrito en ningún plan de estudio. Comuníquese con la administración."
            );
        }

        /*
         * Se bloquean los grupos ANTES de leer la matrícula y los cupos:
         * quien llega segundo espera aquí y, con READ_COMMITTED, ya ve lo
         * que confirmó quien llegó primero.
         */
        List<Grupo> gruposSeleccionados =
                grupoRepository
                        .findAllByIdParaActualizar(
                                idsGrupo
                        );

        if (gruposSeleccionados.size() != idsGrupo.size()) {
            throw new BusinessException(
                    "Uno o más de los grupos seleccionados no existen."
            );
        }

        Matricula matriculaExistente =
                matriculaRepository
                        .findByAutorizacion_Id(
                                autorizacion.getId()
                        )
                        .orElse(null);

        if (matriculaExistente != null
                && !ESTADO_MATRICULA_REGISTRADA.equals(
                matriculaExistente.getEstado().getCodigo()
        )) {

            throw new BusinessException(
                    "Su matrícula de este período no se encuentra activa."
            );
        }

        Map<Long, MallaCurricular> mallaPorCurso =
                mallaPorCurso(
                        idsPlan
                );

        List<Grupo> gruposYaMatriculados =
                matriculaExistente == null
                        ? List.of()
                        : gruposDeMatricula(
                        matriculaExistente
                );

        List<String> errores =
                validarSeleccion(
                        estudiante,
                        periodo,
                        idsPlan,
                        mallaPorCurso,
                        gruposSeleccionados,
                        gruposYaMatriculados
                );

        if (!errores.isEmpty()) {
            throw new MatriculaInvalidaException(
                    errores
            );
        }

        /*
         * Se conserva el orden en que el estudiante eligió los grupos.
         */
        Map<Long, Grupo> gruposPorId =
                gruposSeleccionados
                        .stream()
                        .collect(Collectors.toMap(
                                Grupo::getId,
                                grupo -> grupo
                        ));

        List<Grupo> gruposEnOrden =
                idsGrupo
                        .stream()
                        .map(gruposPorId::get)
                        .toList();

        List<Grupo> gruposParaInsertar =
                ordenarCorrequisitosPrimero(
                        gruposEnOrden,
                        idsPlan
                );

        Matricula matricula;

        try {

            matricula =
                    matriculaExistente != null
                            ? matriculaExistente
                            : crearMatricula(
                            autorizacion,
                            mallaPorCurso
                                    .get(
                                            gruposEnOrden
                                                    .get(0)
                                                    .getIdCurso()
                                    )
                                    .getIdPlanEstudio()
                    );

            for (Grupo grupo : gruposParaInsertar) {

                MatriculaDetalle detalle =
                        new MatriculaDetalle();

                detalle.setMatricula(
                        matricula
                );

                detalle.setIdGrupo(
                        grupo.getId()
                );

                matriculaDetalleRepository.save(
                        detalle
                );
            }

            matriculaDetalleRepository.flush();

        } catch (BusinessException ex) {

            throw ex;

        } catch (RuntimeException ex) {

            /*
             * Los triggers de la base de datos son la última línea de
             * defensa. Si rechazan la matrícula, se le muestra su mensaje
             * al estudiante en lugar de un error interno.
             */
            String reglaDeBaseDeDatos =
                    mensajeDeReglaDeBaseDeDatos(
                            ex
                    );

            if (reglaDeBaseDeDatos != null) {
                throw new MatriculaInvalidaException(
                        List.of(reglaDeBaseDeDatos)
                );
            }

            throw ex;
        }

        Map<Long, PlanEstudio> planes =
                planEstudioRepository
                        .findAllById(idsPlan)
                        .stream()
                        .collect(Collectors.toMap(
                                PlanEstudio::getId,
                                plan -> plan
                        ));

        MatriculaConfirmadaResponseDTO respuesta =
                new MatriculaConfirmadaResponseDTO();

        respuesta.setIdMatricula(
                matricula.getId()
        );

        respuesta.setEstado(
                matricula.getEstado().getCodigo()
        );

        respuesta.setFechaMatricula(
                matricula.getFechaMatricula()
        );

        respuesta.setPeriodo(
                mapearPeriodo(periodo)
        );

        respuesta.setCursos(
                mapearGrupos(
                        gruposEnOrden,
                        mallaPorCurso,
                        planes
                )
        );

        return respuesta;
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
            List<Grupo> seleccionados,
            List<Grupo> yaMatriculados
    ) {

        Set<String> errores = new LinkedHashSet<>();

        Set<Long> idsCurso = new HashSet<>();

        seleccionados.forEach(grupo ->
                idsCurso.add(grupo.getIdCurso())
        );

        yaMatriculados.forEach(grupo ->
                idsCurso.add(grupo.getIdCurso())
        );

        // Para los mensajes de error, incluye el nombre de los requeridos.
        List<RequisitosCurso> requisitos =
                requisitosCursoRepository
                        .findActivosPorPlanesYCursos(
                                idsPlan,
                                idsCurso
                        );

        requisitos.forEach(requisito ->
                idsCurso.add(
                        requisito.getIdCursoRequerido().getId()
                )
        );

        Map<Long, Curso> cursos =
                cursoRepository
                        .findAllById(idsCurso)
                        .stream()
                        .collect(Collectors.toMap(
                                Curso::getId,
                                curso -> curso
                        ));

        Set<Long> cursosSeleccionados =
                seleccionados
                        .stream()
                        .map(Grupo::getIdCurso)
                        .collect(Collectors.toSet());

        Set<Long> cursosMatriculados =
                yaMatriculados
                        .stream()
                        .map(Grupo::getIdCurso)
                        .collect(Collectors.toSet());

        Map<Long, Long> ocupados =
                contarCuposOcupados(
                        seleccionados
                                .stream()
                                .map(Grupo::getId)
                                .toList()
                );

        Set<Long> cursosVistos = new HashSet<>();

        for (Grupo grupo : seleccionados) {

            Curso curso =
                    cursos.get(
                            grupo.getIdCurso()
                    );

            String etiqueta =
                    etiquetaGrupo(
                            grupo,
                            curso
                    );

            if (!Objects.equals(
                    grupo.getIdPeriodo(),
                    periodo.getId()
            )) {
                errores.add(
                        "El grupo " + etiqueta
                                + " no pertenece al período vigente."
                );
            }

            if (!ESTADOS_GRUPO_OFERTADOS.contains(
                    grupo.getEstado()
            )) {
                errores.add(
                        "El grupo " + etiqueta
                                + " no está disponible para matrícula."
                );
            }

            if (curso == null
                    || !Boolean.TRUE.equals(curso.getActivo())) {
                errores.add(
                        "El curso del grupo " + etiqueta
                                + " no se encuentra activo."
                );
            }

            /*
             * Seguridad: solo se pueden matricular cursos de los planes
             * en los que el estudiante está inscrito.
             */
            if (!mallaPorCurso.containsKey(
                    grupo.getIdCurso()
            )) {
                errores.add(
                        "El curso " + etiqueta
                                + " no pertenece a ningún plan de estudio en el que usted está inscrito."
                );
            }

            if (!cursosVistos.add(
                    grupo.getIdCurso()
            )) {
                errores.add(
                        "Seleccionó más de un grupo del curso "
                                + nombreCurso(curso)
                                + "."
                );
            }

            if (cursosMatriculados.contains(
                    grupo.getIdCurso()
            )) {
                errores.add(
                        "Ya está matriculado en el curso "
                                + nombreCurso(curso)
                                + " durante este período."
                );
            }

            long ocupadosGrupo =
                    ocupados.getOrDefault(
                            grupo.getId(),
                            0L
                    );

            if (ocupadosGrupo >= grupo.getCupoMaximo()) {
                errores.add(
                        "El grupo " + etiqueta
                                + " no tiene cupos disponibles."
                );
            }
        }

        validarRequisitos(
                estudiante,
                requisitos,
                cursos,
                cursosSeleccionados,
                cursosMatriculados,
                errores
        );

        validarHorarios(
                seleccionados,
                yaMatriculados,
                cursos,
                errores
        );

        return new ArrayList<>(errores);
    }

    /*
     * Prerrequisitos: el curso requerido debe estar aprobado (con la
     * nota mínima, si el plan la exige).
     *
     * Correquisitos: el curso requerido debe estar aprobado, ya
     * matriculado en el período o incluido en esta misma selección.
     */
    private void validarRequisitos(
            Estudiante estudiante,
            List<RequisitosCurso> requisitos,
            Map<Long, Curso> cursos,
            Set<Long> cursosSeleccionados,
            Set<Long> cursosMatriculados,
            Set<String> errores
    ) {

        Map<Long, BigDecimal> aprobados =
                cursosAprobados(
                        estudiante.getId()
                );

        for (RequisitosCurso requisito : requisitos) {

            Long idCurso =
                    requisito.getIdCurso().getId();

            if (!cursosSeleccionados.contains(idCurso)) {
                continue;
            }

            Long idRequerido =
                    requisito.getIdCursoRequerido().getId();

            String nombreCurso =
                    nombreCurso(
                            cursos.get(idCurso)
                    );

            String nombreRequerido =
                    nombreCurso(
                            cursos.get(idRequerido)
                    );

            String codigoTipo =
                    requisito.getIdTipoRequisito().getCodigo();

            if (TIPO_PRERREQUISITO.equals(codigoTipo)) {

                if (!estaAprobado(
                        aprobados,
                        idRequerido,
                        requisito.getNotaMinima()
                )) {
                    errores.add(
                            "Prerrequisito incumplido en " + nombreCurso
                                    + ": debe haber aprobado " + nombreRequerido + "."
                    );
                }

            } else if (TIPO_CORREQUISITO.equals(codigoTipo)) {

                boolean cumplido =
                        estaAprobado(
                                aprobados,
                                idRequerido,
                                requisito.getNotaMinima()
                        )
                                || cursosMatriculados.contains(idRequerido)
                                || cursosSeleccionados.contains(idRequerido);

                if (!cumplido) {
                    errores.add(
                            "Correquisito incumplido en " + nombreCurso
                                    + ": debe matricularse junto con " + nombreRequerido + "."
                    );
                }
            }
        }
    }

    private boolean estaAprobado(
            Map<Long, BigDecimal> aprobados,
            Long idCurso,
            BigDecimal notaMinima
    ) {

        if (!aprobados.containsKey(idCurso)) {
            return false;
        }

        BigDecimal nota =
                aprobados.get(idCurso);

        // Igual que la validación de la base de datos: si el plan exige una
        // nota mínima, la nota debe existir y alcanzarla.
        return notaMinima == null
                || (nota != null && nota.compareTo(notaMinima) >= 0);
    }

    /*
     * No puede haber conflictos de horario entre los grupos
     * seleccionados ni con los que el estudiante ya tiene matriculados
     * en el período.
     */
    private void validarHorarios(
            List<Grupo> seleccionados,
            List<Grupo> yaMatriculados,
            Map<Long, Curso> cursos,
            Set<String> errores
    ) {

        List<Long> idsGrupo = new ArrayList<>();

        seleccionados.forEach(grupo -> idsGrupo.add(grupo.getId()));
        yaMatriculados.forEach(grupo -> idsGrupo.add(grupo.getId()));

        Map<Long, List<HorarioGrupo>> horarios =
                horariosPorGrupo(
                        idsGrupo
                );

        for (int i = 0; i < seleccionados.size(); i++) {

            Grupo grupo =
                    seleccionados.get(i);

            String etiqueta =
                    etiquetaGrupo(
                            grupo,
                            cursos.get(grupo.getIdCurso())
                    );

            for (int j = i + 1; j < seleccionados.size(); j++) {

                Grupo otro =
                        seleccionados.get(j);

                if (hayChoque(
                        horarios.get(grupo.getId()),
                        horarios.get(otro.getId())
                )) {
                    errores.add(
                            "Choque de horario entre " + etiqueta + " y "
                                    + etiquetaGrupo(
                                    otro,
                                    cursos.get(otro.getIdCurso())
                            ) + "."
                    );
                }
            }

            for (Grupo matriculado : yaMatriculados) {

                if (hayChoque(
                        horarios.get(grupo.getId()),
                        horarios.get(matriculado.getId())
                )) {
                    errores.add(
                            "Choque de horario entre " + etiqueta + " y "
                                    + etiquetaGrupo(
                                    matriculado,
                                    cursos.get(matriculado.getIdCurso())
                            ) + ", que ya tiene matriculado en este período."
                    );
                }
            }
        }
    }

    private boolean hayChoque(
            List<HorarioGrupo> a,
            List<HorarioGrupo> b
    ) {

        if (a == null || b == null) {
            return false;
        }

        return a.stream().anyMatch(ha ->
                b.stream().anyMatch(hb ->
                        ha.getDiaSemana().equals(hb.getDiaSemana())
                                && ha.getHoraInicio().isBefore(hb.getHoraFin())
                                && hb.getHoraInicio().isBefore(ha.getHoraFin())
                )
        );
    }

    /*
     * La base de datos valida los correquisitos al insertar cada curso
     * (trg_matriculas_detalles_bi_validar_requisitos): el curso requerido
     * debe estar ya en la matrícula o aprobado. Por eso se insertan primero
     * los cursos de los que otros de la selección dependen, sin alterar
     * el resto del orden en que el estudiante los eligió.
     */
    private List<Grupo> ordenarCorrequisitosPrimero(
            List<Grupo> grupos,
            List<Long> idsPlan
    ) {

        Set<Long> idsCurso =
                grupos
                        .stream()
                        .map(Grupo::getIdCurso)
                        .collect(Collectors.toSet());

        // curso -> cursos de la selección que debe llevar junto con él.
        Map<Long, Set<Long>> dependencias =
                new HashMap<>();

        requisitosCursoRepository
                .findActivosPorPlanesYCursos(
                        idsPlan,
                        idsCurso
                )
                .stream()
                .filter(requisito ->
                        TIPO_CORREQUISITO.equals(
                                requisito.getIdTipoRequisito().getCodigo()
                        )
                )
                .filter(requisito ->
                        idsCurso.contains(
                                requisito.getIdCursoRequerido().getId()
                        )
                )
                .forEach(requisito ->
                        dependencias
                                .computeIfAbsent(
                                        requisito.getIdCurso().getId(),
                                        id -> new HashSet<>()
                                )
                                .add(
                                        requisito.getIdCursoRequerido().getId()
                                )
                );

        List<Grupo> pendientes =
                new ArrayList<>(grupos);

        List<Grupo> ordenados =
                new ArrayList<>();

        Set<Long> cursosInsertados =
                new HashSet<>();

        while (!pendientes.isEmpty()) {

            // Si dos cursos se exigen mutuamente no existe un orden posible:
            // se sigue el orden original y la base de datos informará el motivo.
            Grupo siguiente =
                    pendientes
                            .stream()
                            .filter(grupo ->
                                    cursosInsertados.containsAll(
                                            dependencias.getOrDefault(
                                                    grupo.getIdCurso(),
                                                    Set.of()
                                            )
                                    )
                            )
                            .findFirst()
                            .orElse(pendientes.get(0));

            pendientes.remove(siguiente);
            ordenados.add(siguiente);
            cursosInsertados.add(siguiente.getIdCurso());
        }

        return ordenados;
    }

    /*
     * Los triggers de la base de datos rechazan con SQLSTATE 45000 y un
     * mensaje propio (por ejemplo, "El estudiante no cumple los
     * prerrequisitos del curso"). Devuelve ese mensaje, o null si el error
     * es de otra naturaleza.
     */
    private String mensajeDeReglaDeBaseDeDatos(
            Throwable error
    ) {

        for (Throwable causa = error;
             causa != null;
             causa = causa.getCause()) {

            if (causa instanceof SQLException sqlException
                    && "45000".equals(sqlException.getSQLState())) {

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
    private Matricula crearMatricula(
            AutorizacionesMatricula autorizacion,
            Long idPlanEstudio
    ) {

        EstadoMatricula registrada =
                estadoMatriculaRepository
                        .findByCodigoAndActivoTrue(
                                ESTADO_MATRICULA_REGISTRADA
                        )
                        .orElseThrow(() ->
                                new BusinessException(
                                        "No se encontró el estado de matrícula requerido."
                                )
                        );

        Matricula matricula =
                new Matricula();

        matricula.setAutorizacion(
                autorizacion
        );

        matricula.setIdPlanEstudio(
                idPlanEstudio
        );

        matricula.setEstado(
                registrada
        );

        try {

            return matriculaRepository.saveAndFlush(
                    matricula
            );

        } catch (DataIntegrityViolationException ex) {

            throw new BusinessException(
                    "No fue posible registrar la matrícula. Intente nuevamente."
            );
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
    private String motivoSinAutorizacion(
            Optional<AutorizacionesMatricula> autorizacion
    ) {

        if (autorizacion.isEmpty()) {
            return "El estudiante no está habilitado para matricular en este período. "
                    + "Requiere la revisión previa del administrador académico.";
        }

        String codigo =
                autorizacion
                        .get()
                        .getIdEstadoAutorizacion()
                        .getCodigo();

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

    private OfertaMatriculaResponseDTO noHabilitado(
            OfertaMatriculaResponseDTO respuesta,
            String motivo
    ) {

        respuesta.setHabilitado(false);

        respuesta.setMotivoNoHabilitado(
                motivo
        );

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
                .findByEstadoInOrderByFechaInicioDesc(
                        List.of(ESTADO_PERIODO_MATRICULA)
                )
                .stream()
                .findFirst();
    }

    private List<Long> idsPlanesInscritos(
            Long idEstudiante
    ) {

        return estudiantePlanRepository
                .findByIdEstudianteAndActivoTrue(
                        idEstudiante
                )
                .stream()
                .map(EstudiantePlanEstudio::getIdPlanEstudio)
                .distinct()
                .toList();
    }

    /*
     * Cursos disponibles para el estudiante: los de la malla de los
     * planes en los que está inscrito. Si un curso está en varios de
     * esos planes se toma el de menor id, de forma determinista.
     */
    private Map<Long, MallaCurricular> mallaPorCurso(
            Collection<Long> idsPlan
    ) {

        Map<Long, MallaCurricular> resultado =
                new HashMap<>();

        mallaRepository
                .findByIdPlanEstudioInAndActivoTrue(
                        idsPlan
                )
                .stream()
                .sorted(Comparator.comparing(
                        MallaCurricular::getIdPlanEstudio
                ))
                .forEach(malla ->
                        resultado.putIfAbsent(
                                malla.getIdCurso(),
                                malla
                        )
                );

        return resultado;
    }

    /*
     * Grupos que el estudiante ya tiene matriculados (no retirados) en
     * el período de la autorización.
     */
    private List<Grupo> gruposYaMatriculados(
            AutorizacionesMatricula autorizacion
    ) {

        return matriculaRepository
                .findByAutorizacion_Id(
                        autorizacion.getId()
                )
                .filter(matricula ->
                        ESTADO_MATRICULA_REGISTRADA.equals(
                                matricula.getEstado().getCodigo()
                        )
                )
                .map(this::gruposDeMatricula)
                .orElse(List.of());
    }

    private List<Grupo> gruposDeMatricula(
            Matricula matricula
    ) {

        List<Long> idsGrupo =
                matriculaDetalleRepository
                        .findByMatricula_IdAndRetiradoFalse(
                                matricula.getId()
                        )
                        .stream()
                        .map(MatriculaDetalle::getIdGrupo)
                        .toList();

        if (idsGrupo.isEmpty()) {
            return List.of();
        }

        return grupoRepository.findAllById(
                idsGrupo
        );
    }

    private Map<Long, Long> contarCuposOcupados(
            Collection<Long> idsGrupo
    ) {

        if (idsGrupo.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> resultado =
                new HashMap<>();

        for (Object[] fila : matriculaDetalleRepository
                .contarCuposOcupados(
                        idsGrupo,
                        ESTADO_MATRICULA_REGISTRADA
                )) {

            resultado.put(
                    ((Number) fila[0]).longValue(),
                    ((Number) fila[1]).longValue()
            );
        }

        return resultado;
    }

    /*
     * Cursos aprobados o equiparados: idCurso -> mejor nota (puede ser
     * nula en cursos equiparados o con valoración cualitativa).
     */
    private Map<Long, BigDecimal> cursosAprobados(
            Long idEstudiante
    ) {

        Map<Long, BigDecimal> resultado =
                new LinkedHashMap<>();

        for (Object[] fila : matriculaDetalleRepository
                .findCursosAprobados(
                        idEstudiante
                )) {

            resultado.put(
                    ((Number) fila[0]).longValue(),
                    fila[1] == null
                            ? null
                            : new BigDecimal(fila[1].toString())
            );
        }

        return resultado;
    }

    private Map<Long, List<HorarioGrupo>> horariosPorGrupo(
            Collection<Long> idsGrupo
    ) {

        if (idsGrupo.isEmpty()) {
            return Map.of();
        }

        return horarioGrupoRepository
                .findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(
                        idsGrupo
                )
                .stream()
                .collect(Collectors.groupingBy(
                        HorarioGrupo::getIdGrupo
                ));
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

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {

            throw new BusinessException(
                    "No se pudo identificar al usuario autenticado."
            );
        }

        return estudianteRepository
                .findByUsuario_Cedula(
                        authentication.getName()
                )
                .orElseThrow(() ->
                        new BusinessException(
                                "El usuario autenticado no está asociado a un estudiante."
                        )
                );
    }

    /*
     * ============================================================
     * Conversión a DTO
     * ============================================================
     */
    private List<OfertaMatriculaResponseDTO.GrupoDTO> mapearGrupos(
            List<Grupo> grupos,
            Map<Long, MallaCurricular> mallaPorCurso,
            Map<Long, PlanEstudio> planes
    ) {

        if (grupos.isEmpty()) {
            return List.of();
        }

        List<Long> idsGrupo =
                grupos.stream().map(Grupo::getId).toList();

        Set<Long> idsCurso =
                grupos.stream()
                        .map(Grupo::getIdCurso)
                        .collect(Collectors.toSet());

        Set<Long> idsProfesor =
                grupos.stream()
                        .map(Grupo::getIdProfesor)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        Map<Long, Curso> cursos =
                cursoRepository
                        .findAllById(idsCurso)
                        .stream()
                        .collect(Collectors.toMap(
                                Curso::getId,
                                curso -> curso
                        ));

        Map<Long, Profesor> profesores =
                idsProfesor.isEmpty()
                        ? Map.of()
                        : profesorRepository
                        .findAllById(idsProfesor)
                        .stream()
                        .collect(Collectors.toMap(
                                Profesor::getId,
                                profesor -> profesor
                        ));

        Map<Long, List<HorarioGrupo>> horarios =
                horariosPorGrupo(idsGrupo);

        Map<Long, Long> ocupados =
                contarCuposOcupados(idsGrupo);

        List<OfertaMatriculaResponseDTO.GrupoDTO> resultado =
                new ArrayList<>();

        for (Grupo grupo : grupos) {

            OfertaMatriculaResponseDTO.GrupoDTO dto =
                    new OfertaMatriculaResponseDTO.GrupoDTO();

            dto.setIdGrupo(grupo.getId());
            dto.setCodigo(grupo.getCodigo());
            dto.setIdCurso(grupo.getIdCurso());
            dto.setModalidad(grupo.getModalidad());
            dto.setAula(grupo.getAula());

            Curso curso =
                    cursos.get(grupo.getIdCurso());

            if (curso != null) {
                dto.setCodigoCurso(curso.getCodigo());
                dto.setNombreCurso(curso.getNombre());
            }

            MallaCurricular malla =
                    mallaPorCurso.get(grupo.getIdCurso());

            if (malla != null) {

                dto.setNivel(malla.getNivel());
                dto.setIdPlanEstudio(malla.getIdPlanEstudio());

                PlanEstudio plan =
                        planes.get(malla.getIdPlanEstudio());

                if (plan != null) {
                    dto.setNombrePlan(plan.getNombre());
                }
            }

            // Los grupos creados en HU-028 aún no tienen profesor asignado.
            Profesor profesor =
                    grupo.getIdProfesor() == null
                            ? null
                            : profesores.get(grupo.getIdProfesor());

            if (profesor != null) {
                dto.setProfesor(
                        nombreCompleto(
                                profesor.getNombres(),
                                profesor.getPrimerApellido(),
                                profesor.getSegundoApellido()
                        )
                );
            }

            int cupoMaximo =
                    grupo.getCupoMaximo();

            long ocupadosGrupo =
                    ocupados.getOrDefault(grupo.getId(), 0L);

            dto.setCupoMaximo(cupoMaximo);

            dto.setCupoDisponible(
                    (int) Math.max(0, cupoMaximo - ocupadosGrupo)
            );

            for (HorarioGrupo horario :
                    horarios.getOrDefault(grupo.getId(), List.of())) {

                OfertaMatriculaResponseDTO.HorarioDTO h =
                        new OfertaMatriculaResponseDTO.HorarioDTO();

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

    private Map<Long, List<OfertaMatriculaResponseDTO.RequisitoDTO>>
    mapearRequisitos(
            Collection<Long> idsPlan,
            Collection<Long> idsCurso
    ) {

        Map<Long, List<OfertaMatriculaResponseDTO.RequisitoDTO>> resultado =
                new LinkedHashMap<>();

        if (idsCurso.isEmpty()) {
            return resultado;
        }

        Set<String> vistos = new HashSet<>();

        for (RequisitosCurso requisito : requisitosCursoRepository
                .findActivosPorPlanesYCursos(
                        idsPlan,
                        idsCurso
                )) {

            String tipo =
                    TIPO_PRERREQUISITO.equals(
                            requisito.getIdTipoRequisito().getCodigo()
                    )
                            ? REQUISITO_PANTALLA
                            : CORREQUISITO_PANTALLA;

            Long idCurso =
                    requisito.getIdCurso().getId();

            Curso requerido =
                    requisito.getIdCursoRequerido();

            // Un mismo requisito puede repetirse en varios planes.
            if (!vistos.add(idCurso + "|" + tipo + "|" + requerido.getId())) {
                continue;
            }

            OfertaMatriculaResponseDTO.RequisitoDTO dto =
                    new OfertaMatriculaResponseDTO.RequisitoDTO();

            dto.setTipo(tipo);
            dto.setIdCursoRequerido(requerido.getId());
            dto.setCodigoCursoRequerido(requerido.getCodigo());
            dto.setNombreCursoRequerido(requerido.getNombre());
            dto.setNotaMinima(requisito.getNotaMinima());

            resultado
                    .computeIfAbsent(
                            idCurso,
                            id -> new ArrayList<>()
                    )
                    .add(dto);
        }

        return resultado;
    }

    private OfertaMatriculaResponseDTO.EstudianteDTO mapearEstudiante(
            Estudiante estudiante
    ) {

        OfertaMatriculaResponseDTO.EstudianteDTO dto =
                new OfertaMatriculaResponseDTO.EstudianteDTO();

        dto.setId(estudiante.getId());
        dto.setNumeroIdentificacion(estudiante.getNumeroIdentificacion());
        dto.setActivo(Boolean.TRUE.equals(estudiante.getActivo()));

        dto.setNombreCompleto(
                nombreCompleto(
                        estudiante.getNombres(),
                        estudiante.getPrimerApellido(),
                        estudiante.getSegundoApellido()
                )
        );

        return dto;
    }

    private OfertaMatriculaResponseDTO.PeriodoDTO mapearPeriodo(
            PeriodoAcademico periodo
    ) {

        OfertaMatriculaResponseDTO.PeriodoDTO dto =
                new OfertaMatriculaResponseDTO.PeriodoDTO();

        dto.setId(periodo.getId());
        dto.setCodigo(periodo.getCodigo());
        dto.setNombre(periodo.getNombre());
        dto.setFechaInicioMatricula(periodo.getFechaInicioMatricula());
        dto.setFechaFinMatricula(periodo.getFechaFinMatricula());

        return dto;
    }

    private OfertaMatriculaResponseDTO.PlanDTO mapearPlan(
            PlanEstudio plan
    ) {

        OfertaMatriculaResponseDTO.PlanDTO dto =
                new OfertaMatriculaResponseDTO.PlanDTO();

        dto.setId(plan.getId());
        dto.setCodigo(plan.getCodigo());
        dto.setNombre(plan.getNombre());

        return dto;
    }

    private String nombreCompleto(
            String nombres,
            String primerApellido,
            String segundoApellido
    ) {

        return java.util.stream.Stream
                .of(nombres, primerApellido, segundoApellido)
                .filter(parte -> parte != null && !parte.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }

    private String nombreCurso(
            Curso curso
    ) {

        return curso == null
                ? "desconocido"
                : "«" + curso.getNombre() + "»";
    }

    private String etiquetaGrupo(
            Grupo grupo,
            Curso curso
    ) {

        return nombreCurso(curso)
                + " (" + grupo.getCodigo() + ")";
    }
}
