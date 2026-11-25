package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.HorarioGrupo;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.HorarioGrupoRepository;
import cr.acusab.una.sigac.data.repository.MatriculaDetalleRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.request.GrupoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.request.HorarioGrupoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.GrupoGestionResponse;
import cr.acusab.una.sigac.presentation.dto.response.GrupoResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class GrupoService {

    private static final Set<String> MODALIDADES_VALIDAS = Set.of(
            "PRESENCIAL",
            "VIRTUAL",
            "HIBRIDA"
    );

    /*
     * Estados permitidos al crear un grupo.
     * EN_CURSO, CERRADO y CANCELADO llegan después, por acciones
     * explícitas sobre un grupo ya existente.
     */
    private static final Set<String> ESTADOS_VALIDOS_CREACION = Set.of(
            "PLANIFICADO",
            "PUBLICADO"
    );

    // En períodos terminados no tiene sentido abrir grupos nuevos.
    private static final Set<String> ESTADOS_PERIODO_CERRADOS = Set.of(
            "CERRADO",
            "CANCELADO"
    );

    // Franja permitida para las lecciones: no se imparten en la madrugada.
    private static final LocalTime HORA_MINIMA = LocalTime.of(7, 0);
    private static final LocalTime HORA_MAXIMA = LocalTime.of(22, 0);

    private final GrupoRepository grupoRepository;
    private final HorarioGrupoRepository horarioGrupoRepository;
    private final CursoRepository cursoRepository;
    private final PeriodoAcademicoRepository periodoAcademicoRepository;
    private final ProfesorRepository profesorRepository;
    /*
     * Estados permitidos al modificar un grupo. Un grupo CERRADO o
     * CANCELADO ya no se modifica (es un estado final).
     */
    private static final Set<String> ESTADOS_VALIDOS_ACTUALIZACION = Set.of(
            "PLANIFICADO",
            "PUBLICADO",
            "EN_CURSO",
            "CERRADO",
            "CANCELADO"
    );

    private static final Set<String> ESTADOS_GRUPO_FINALES = Set.of(
            "CERRADO",
            "CANCELADO"
    );

    // Estados en que el grupo se imparte y por eso necesita profesor.
    private static final Set<String> ESTADOS_QUE_REQUIEREN_PROFESOR = Set.of(
            "PUBLICADO",
            "EN_CURSO"
    );

    // Igual que ConsultaCursosService: un cupo lo ocupa una matrícula REGISTRADA.
    private static final String ESTADO_MATRICULA_REGISTRADA = "REGISTRADA";

    private static final int LONGITUD_MAXIMA_CODIGO = 40;

    private final ValidacionHorarioProfesorService validacionHorarioProfesorService;
    private final UsuarioRepository usuarioRepository;
    private final MatriculaDetalleRepository matriculaDetalleRepository;

    public GrupoService(
            GrupoRepository grupoRepository,
            HorarioGrupoRepository horarioGrupoRepository,
            CursoRepository cursoRepository,
            PeriodoAcademicoRepository periodoAcademicoRepository,
            ProfesorRepository profesorRepository,
            ValidacionHorarioProfesorService validacionHorarioProfesorService,
            UsuarioRepository usuarioRepository,
            MatriculaDetalleRepository matriculaDetalleRepository
    ) {
        this.grupoRepository = grupoRepository;
        this.horarioGrupoRepository = horarioGrupoRepository;
        this.cursoRepository = cursoRepository;
        this.periodoAcademicoRepository = periodoAcademicoRepository;
        this.profesorRepository = profesorRepository;
        this.validacionHorarioProfesorService = validacionHorarioProfesorService;
        this.usuarioRepository = usuarioRepository;
        this.matriculaDetalleRepository = matriculaDetalleRepository;
    }

    /* =================================================================
     * Gestión de grupos: listar, sugerir código, modificar y eliminar
     * ================================================================= */

    /*
     * Listado de grupos para la pantalla de gestión. Ambos filtros son
     * opcionales.
     */
    @Transactional(readOnly = true)
    public List<GrupoGestionResponse> listar(Long idPeriodo, Long idCurso) {

        // Los espacios de instrumento y las agrupaciones tienen su propia pantalla.
        List<Grupo> grupos = grupoRepository.findAllByOrderByIdPeriodoDescCodigoAsc()
                .stream()
                .filter(g -> !g.esEspacioInstrumento())
                .filter(g -> !g.esAgrupacion())
                .filter(g -> idPeriodo == null || idPeriodo.equals(g.getIdPeriodo()))
                .filter(g -> idCurso == null || idCurso.equals(g.getIdCurso()))
                .toList();

        return mapearAGestion(grupos);
    }

    @Transactional(readOnly = true)
    public GrupoGestionResponse obtener(Long idGrupo) {
        return mapearAGestion(List.of(buscarGrupoOLanzarError(idGrupo))).get(0);
    }

    /*
     * Código sugerido para un grupo nuevo: código del curso + la primera
     * letra libre en el período (MUS-101-A, MUS-101-B…). Es la misma
     * convención que usa Crear curso para su primer grupo.
     */
    public String sugerirCodigo(Long idCurso, Long idPeriodo) {

        Curso curso = cursoRepository
                .findById(idCurso)
                .orElseThrow(() -> new BusinessException("El curso seleccionado no existe."));

        String base = curso.getCodigo().trim().toUpperCase(Locale.ROOT);
        if (base.length() > LONGITUD_MAXIMA_CODIGO - 4) {
            base = base.substring(0, LONGITUD_MAXIMA_CODIGO - 4);
        }

        if (idPeriodo == null) {
            return base + "-A";
        }

        for (char letra = 'A'; letra <= 'Z'; letra++) {
            String codigo = base + "-" + letra;
            if (!grupoRepository.existsByIdPeriodoAndCodigo(idPeriodo, codigo)) {
                return codigo;
            }
        }

        // Más de 26 grupos del mismo curso en un período: se numeran.
        int numero = 27;
        while (grupoRepository.existsByIdPeriodoAndCodigo(idPeriodo, base + "-" + numero)) {
            numero++;
        }
        return base + "-" + numero;
    }

    /*
     * Modificar un grupo y reemplazar su horario semanal.
     *
     * Reglas:
     * - Un grupo CERRADO o CANCELADO no se modifica.
     * - Si el grupo tiene matrículas, no se cambia su curso ni su período,
     *   y el cupo no puede quedar por debajo de los estudiantes inscritos.
     * - PUBLICADO y EN_CURSO requieren un profesor activo.
     */
    @Transactional
    public GrupoGestionResponse actualizar(Long idGrupo, GrupoRequestDTO request) {

        Grupo grupo = buscarGrupoOLanzarError(idGrupo);
        rechazarEspacio(grupo);

        if (ESTADOS_GRUPO_FINALES.contains(grupo.getEstado())) {
            throw new BusinessException(
                    "Un grupo cerrado o cancelado no se puede modificar."
            );
        }

        String codigo = request.getCodigo().trim().toUpperCase(Locale.ROOT);
        String modalidad = request.getModalidad().trim().toUpperCase(Locale.ROOT);
        String estado = request.getEstado().trim().toUpperCase(Locale.ROOT);
        String aula = textoOpcional(request.getAula());
        LocalDate fechaInicio = request.getFechaInicio();
        LocalDate fechaFin = request.getFechaFin();

        boolean cambiaCurso = !grupo.getIdCurso().equals(request.getIdCurso());
        boolean cambiaPeriodo = !grupo.getIdPeriodo().equals(request.getIdPeriodo());

        // 1. Con matrículas, el grupo queda atado a su curso y período.
        boolean tieneMatriculas = contar(
                matriculaDetalleRepository.contarDetallesPorGrupo(List.of(idGrupo))
        ).getOrDefault(idGrupo, 0L) > 0;

        if (tieneMatriculas && (cambiaCurso || cambiaPeriodo)) {
            throw new BusinessException(
                    "El grupo tiene matrículas registradas: no se puede cambiar su curso ni su período."
            );
        }

        // 2. Curso: si cambia, el nuevo debe existir y estar activo.
        Curso curso = cursoRepository
                .findById(request.getIdCurso())
                .orElseThrow(() -> new BusinessException("El curso seleccionado no existe."));

        if (cambiaCurso && !Boolean.TRUE.equals(curso.getActivo())) {
            throw new BusinessException("El curso seleccionado se encuentra inactivo.");
        }

        if (cambiaCurso) {
            rechazarCursoInstrumento(curso);
        }

        // 2.1 Horario obligatorio, salvo en los cursos a coordinar en Dirección.
        List<HorarioGrupoRequestDTO> horariosPedidos = request.getHorarios();
        exigirHorarioSiCorresponde(curso, horariosPedidos);

        // 2.2 Cupo obligatorio, salvo en los cursos a coordinar (no tienen cupo).
        short cupoMaximo = cupoSegunCurso(curso, request.getCupoMaximo());

        // 3. Período: si cambia, no puede estar cerrado ni cancelado.
        PeriodoAcademico periodo = periodoAcademicoRepository
                .findById(request.getIdPeriodo())
                .orElseThrow(() -> new BusinessException("El período académico seleccionado no existe."));

        if (cambiaPeriodo && ESTADOS_PERIODO_CERRADOS.contains(periodo.getEstado())) {
            throw new BusinessException(
                    "No se pueden mover grupos a un período cerrado o cancelado."
            );
        }

        // 4. Profesor opcional, pero activo.
        Profesor profesor = null;

        if (request.getIdProfesor() != null) {
            profesor = profesorRepository
                    .findByIdParaAsignacion(request.getIdProfesor())
                    .orElseThrow(() -> new BusinessException("El profesor seleccionado no existe."));

            if (!Boolean.TRUE.equals(profesor.getActivo())) {
                throw new BusinessException("El profesor seleccionado se encuentra inactivo.");
            }
        }

        // 5. Modalidad y estado.
        if (!MODALIDADES_VALIDAS.contains(modalidad)) {
            throw new BusinessException("La modalidad indicada no es válida.");
        }

        if (!ESTADOS_VALIDOS_ACTUALIZACION.contains(estado)) {
            throw new BusinessException("El estado indicado no es válido.");
        }

        // En un curso a coordinar, el profesor se asigna por estudiante.
        if (ESTADOS_QUE_REQUIEREN_PROFESOR.contains(estado) && profesor == null && !curso.esACoordinar()) {
            throw new BusinessException(
                    "Un grupo publicado o en curso debe tener un profesor activo asignado."
            );
        }

        // 6. El cupo no puede quedar por debajo de los inscritos (un grupo sin cupo no se llena).
        if (!curso.esACoordinar()) {
            long inscritos = contar(
                    matriculaDetalleRepository.contarCuposOcupados(List.of(idGrupo), ESTADO_MATRICULA_REGISTRADA)
            ).getOrDefault(idGrupo, 0L);

            if (cupoMaximo < inscritos) {
                throw new BusinessException(
                        "El cupo no puede ser menor que los " + inscritos + " estudiantes inscritos en el grupo."
                );
            }
        }

        // 7. Fechas, código único en el período y horario.
        validarFechas(fechaInicio, fechaFin, periodo);

        if (grupoRepository.existsByIdPeriodoAndCodigoAndIdNot(periodo.getId(), codigo, idGrupo)) {
            throw new BusinessException(
                    "Ya existe otro grupo con ese código en el período seleccionado."
            );
        }

        validarHorarios(horariosPedidos);

        // Sin horario (solo en un curso a coordinar) no hay choques que revisar.
        if (profesor != null && !horariosPedidos.isEmpty()) {
            validarDisponibilidadProfesor(profesor, periodo, fechaInicio, fechaFin, idGrupo, horariosPedidos);
        }

        // 8. Guardar el grupo.
        grupo.setIdUsuarioUltimaModificacion(obtenerIdUsuarioAutenticado());
        grupo.setIdCurso(curso.getId());
        grupo.setIdPeriodo(periodo.getId());
        grupo.setIdProfesor(profesor != null ? profesor.getId() : null);
        grupo.setCodigo(codigo);
        grupo.setCupoMaximo(cupoMaximo);
        grupo.setModalidad(modalidad);
        grupo.setAula(aula);
        grupo.setFechaInicio(fechaInicio);
        grupo.setFechaFin(fechaFin);
        grupo.setEstado(estado);
        grupo.setActualizadoEn(Instant.now());

        Grupo guardado = grupoRepository.save(grupo);

        // 9. Reemplazar el horario semanal.
        horarioGrupoRepository.eliminarPorGrupo(idGrupo);
        horarioGrupoRepository.saveAll(crearHorarios(guardado.getId(), horariosPedidos, aula));

        return mapearAGestion(List.of(guardado)).get(0);
    }

    /*
     * Eliminar un grupo. Solo se permite si nunca tuvo matrículas, ni
     * sesiones de clase, ni avances curriculares: de lo contrario se
     * perdería historial académico. En ese caso, el grupo se cancela.
     *
     * El horario se borra en cascada (fk_horarios_grupos_grupos).
     */
    @Transactional
    public void eliminar(Long idGrupo) {

        Grupo grupo = buscarGrupoOLanzarError(idGrupo);
        rechazarEspacio(grupo);
        List<Long> ids = List.of(idGrupo);

        if (contar(matriculaDetalleRepository.contarDetallesPorGrupo(ids)).getOrDefault(idGrupo, 0L) > 0) {
            throw new BusinessException(
                    "El grupo tiene matrículas registradas y no se puede eliminar. Si ya no se impartirá, cámbielo al estado Cancelado."
            );
        }

        if (contar(grupoRepository.contarSesionesPorGrupo(ids)).getOrDefault(idGrupo, 0L) > 0
                || contar(grupoRepository.contarAvancesPorGrupo(ids)).getOrDefault(idGrupo, 0L) > 0) {
            throw new BusinessException(
                    "El grupo ya tiene clases o avances registrados y no se puede eliminar. Cámbielo al estado Cancelado."
            );
        }

        // Se registra quién elimina: el trigger de bitácora del DELETE
        // usa id_usuario_ultima_modificacion.
        grupo.setIdUsuarioUltimaModificacion(obtenerIdUsuarioAutenticado());
        grupoRepository.saveAndFlush(grupo);

        grupoRepository.delete(grupo);
    }

    // Los espacios de instrumento y las agrupaciones se gestionan en su propia pantalla.
    private static void rechazarEspacio(Grupo grupo) {
        if (grupo.esEspacioInstrumento()) {
            throw new BusinessException(
                    "Ese grupo es un espacio de instrumento: modifíquelo o elimínelo desde «Espacios de instrumento»."
            );
        }
        if (grupo.esAgrupacion()) {
            throw new BusinessException(
                    "Ese grupo es de una agrupación: modifíquelo desde «Agrupaciones»."
            );
        }
    }

    /*
     * Instrumento I a XIV no se abren como grupos: cada estudiante lleva su
     * nivel en un espacio individual de su profesor.
     */
    private static void rechazarCursoInstrumento(Curso curso) {
        if (Boolean.TRUE.equals(curso.getEsCursoInstrumento())) {
            throw new BusinessException(
                    "«" + curso.getNombre() + "» es un curso de instrumento: no se abre como grupo. "
                            + "Cree los espacios del profesor en «Espacios de instrumento»."
            );
        }
        // Conjunto Instrumental y Coro: se cursan en las agrupaciones.
        if (Boolean.TRUE.equals(curso.getEsCursoAgrupacion())) {
            throw new BusinessException(
                    "«" + curso.getNombre() + "» se cursa en las agrupaciones: no se abre como grupo. "
                            + "Abra la agrupación en el período desde «Agrupaciones»."
            );
        }
    }

    /*
     * Todos los cursos exigen al menos un horario, menos los cursos a
     * coordinar en Dirección (Música de Cámara): sus grupos pueden no
     * tenerlo. El mensaje es el mismo que daba la validación del DTO.
     */
    private static void exigirHorarioSiCorresponde(Curso curso, List<HorarioGrupoRequestDTO> horarios) {
        if (!curso.esACoordinar() && horarios.isEmpty()) {
            throw new BusinessException("Agregue al menos un horario para el grupo.");
        }
    }

    /*
     * Cupo que se guarda: en un curso a coordinar en Dirección se ignora el
     * que venga y se guarda CUPO_SIN_LIMITE (nunca se usa para contar). En
     * los demás cursos es obligatorio, con el mismo mensaje que daba el DTO.
     */
    private static short cupoSegunCurso(Curso curso, Short cupoPedido) {
        if (curso.esACoordinar()) {
            return CupoGrupo.CUPO_SIN_LIMITE;
        }
        if (cupoPedido == null) {
            throw new BusinessException("El cupo máximo es obligatorio.");
        }
        return cupoPedido;
    }

    private Grupo buscarGrupoOLanzarError(Long idGrupo) {
        return grupoRepository
                .findById(idGrupo)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "El grupo indicado no existe."
                ));
    }

    // Convierte filas [id, cantidad] en un mapa id → cantidad.
    private static Map<Long, Long> contar(List<Object[]> filas) {
        Map<Long, Long> mapa = new HashMap<>();
        for (Object[] fila : filas) {
            mapa.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
        }
        return mapa;
    }

    private List<GrupoGestionResponse> mapearAGestion(List<Grupo> grupos) {

        if (grupos.isEmpty()) {
            return List.of();
        }

        List<Long> ids = grupos.stream().map(Grupo::getId).toList();

        Map<Long, Curso> cursos = new HashMap<>();
        cursoRepository.findAllById(grupos.stream().map(Grupo::getIdCurso).filter(Objects::nonNull).distinct().toList())
                .forEach(c -> cursos.put(c.getId(), c));

        Map<Long, PeriodoAcademico> periodos = new HashMap<>();
        periodoAcademicoRepository.findAllById(grupos.stream().map(Grupo::getIdPeriodo).distinct().toList())
                .forEach(p -> periodos.put(p.getId(), p));

        Map<Long, Profesor> profesores = new HashMap<>();
        profesorRepository.findAllById(grupos.stream().map(Grupo::getIdProfesor).filter(Objects::nonNull).distinct().toList())
                .forEach(p -> profesores.put(p.getId(), p));

        Map<Long, List<GrupoResponseDTO.Horario>> horarios = new HashMap<>();
        horarioGrupoRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(ids)
                .forEach(h -> horarios.computeIfAbsent(h.getIdGrupo(), k -> new ArrayList<>())
                        .add(new GrupoResponseDTO.Horario(h.getId(), h.getDiaSemana(), h.getHoraInicio(), h.getHoraFin(), h.getAula())));

        Map<Long, Long> inscritos = contar(matriculaDetalleRepository.contarCuposOcupados(ids, ESTADO_MATRICULA_REGISTRADA));
        Map<Long, Long> detalles = contar(matriculaDetalleRepository.contarDetallesPorGrupo(ids));
        Map<Long, Long> sesiones = contar(grupoRepository.contarSesionesPorGrupo(ids));
        Map<Long, Long> avances = contar(grupoRepository.contarAvancesPorGrupo(ids));

        return grupos.stream().map(g -> {
            Curso curso = cursos.get(g.getIdCurso());
            PeriodoAcademico periodo = periodos.get(g.getIdPeriodo());
            Profesor profesor = g.getIdProfesor() != null ? profesores.get(g.getIdProfesor()) : null;

            boolean tieneMatriculas = detalles.getOrDefault(g.getId(), 0L) > 0;
            boolean tieneClases = sesiones.getOrDefault(g.getId(), 0L) > 0
                    || avances.getOrDefault(g.getId(), 0L) > 0;

            // Curso a coordinar: sin cupo (no se manda el 999 guardado).
            boolean sinCupo = CupoGrupo.esSinCupo(g, curso);

            return new GrupoGestionResponse(
                    g.getId(),
                    g.getCodigo(),
                    g.getIdCurso(),
                    curso != null ? curso.getCodigo() : null,
                    curso != null ? curso.getNombre() : null,
                    g.getIdPeriodo(),
                    periodo != null ? periodo.getNombre() : null,
                    periodo != null ? periodo.getEstado() : null,
                    periodo != null ? periodo.getFechaInicio() : null,
                    periodo != null ? periodo.getFechaFin() : null,
                    g.getIdProfesor(),
                    nombreCompleto(profesor),
                    sinCupo ? null : g.getCupoMaximo(),
                    inscritos.getOrDefault(g.getId(), 0L),
                    g.getModalidad(),
                    g.getAula(),
                    g.getFechaInicio(),
                    g.getFechaFin(),
                    g.getEstado(),
                    horarios.getOrDefault(g.getId(), List.of()),
                    !ESTADOS_GRUPO_FINALES.contains(g.getEstado()),
                    !tieneMatriculas && !tieneClases,
                    tieneMatriculas,
                    curso != null && curso.esACoordinar(),
                    sinCupo
            );
        }).toList();
    }

    private static String nombreCompleto(Profesor profesor) {
        if (profesor == null) {
            return null;
        }
        return String.join(
                " ",
                profesor.getNombres(),
                profesor.getPrimerApellido(),
                profesor.getSegundoApellido() != null ? profesor.getSegundoApellido() : ""
        ).trim().replaceAll("\\s+", " ");
    }

    private void validarDisponibilidadProfesor(
            Profesor profesor,
            PeriodoAcademico periodo,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            Long idGrupoExcluido,
            List<HorarioGrupoRequestDTO> horarios
    ) {
        List<ValidacionHorarioProfesorService.BloqueHorario> horariosPropuestos =
                horarios.stream()
                        .map(horario -> new ValidacionHorarioProfesorService.BloqueHorario(
                                horario.getDiaSemana(),
                                horario.getHoraInicio(),
                                horario.getHoraFin()
                        ))
                        .toList();

        validacionHorarioProfesorService.validarDisponibilidad(
                profesor.getId(),
                periodo.getId(),
                fechaInicio != null ? fechaInicio : periodo.getFechaInicio(),
                fechaFin != null ? fechaFin : periodo.getFechaFin(),
                idGrupoExcluido,
                horariosPropuestos
        );
    }

    // Un bloque sin aula propia usa el aula del grupo.
    private List<HorarioGrupo> crearHorarios(Long idGrupo, List<HorarioGrupoRequestDTO> horarios, String aulaGrupo) {
        return horarios.stream()
                .map(h -> {
                    HorarioGrupo horario = new HorarioGrupo();
                    horario.setIdGrupo(idGrupo);
                    horario.setDiaSemana(h.getDiaSemana());
                    horario.setHoraInicio(h.getHoraInicio());
                    horario.setHoraFin(h.getHoraFin());

                    String aulaHorario = textoOpcional(h.getAula());
                    horario.setAula(aulaHorario != null ? aulaHorario : aulaGrupo);

                    return horario;
                })
                .toList();
    }

    /*
     * Creación de un grupo con su horario semanal.
     *
     * @Transactional garantiza que si falla el guardado de algún
     * horario, no quede el grupo creado sin horario.
     */
    @Transactional
    public GrupoResponseDTO crear(GrupoRequestDTO request) {

        String codigo = request.getCodigo().trim().toUpperCase();
        String modalidad = request.getModalidad().trim().toUpperCase();
        String estado = request.getEstado().trim().toUpperCase();
        String aula = textoOpcional(request.getAula());
        LocalDate fechaInicio = request.getFechaInicio();
        LocalDate fechaFin = request.getFechaFin();

        // 1. El curso debe existir y estar activo.
        Curso curso = cursoRepository
                .findById(request.getIdCurso())
                .orElseThrow(() -> new BusinessException(
                        "El curso seleccionado no existe."
                ));

        if (!Boolean.TRUE.equals(curso.getActivo())) {
            throw new BusinessException(
                    "El curso seleccionado se encuentra inactivo."
            );
        }

        // 1.1 Los cursos de instrumento van en espacios, no en grupos.
        rechazarCursoInstrumento(curso);

        // 1.2 Horario obligatorio, salvo en los cursos a coordinar en Dirección.
        List<HorarioGrupoRequestDTO> horariosPedidos = request.getHorarios();
        exigirHorarioSiCorresponde(curso, horariosPedidos);

        // 1.3 Cupo obligatorio, salvo en los cursos a coordinar (no tienen cupo).
        short cupoMaximo = cupoSegunCurso(curso, request.getCupoMaximo());

        // 2. El período debe existir y no estar cerrado ni cancelado.
        PeriodoAcademico periodo = periodoAcademicoRepository
                .findById(request.getIdPeriodo())
                .orElseThrow(() -> new BusinessException(
                        "El período académico seleccionado no existe."
                ));

        if (ESTADOS_PERIODO_CERRADOS.contains(periodo.getEstado())) {
            throw new BusinessException(
                    "No se pueden crear grupos en un período cerrado o cancelado."
            );
        }

        // 3. Si se indica profesor, debe existir y estar activo.
        Profesor profesor = null;

        if (request.getIdProfesor() != null) {

            profesor = profesorRepository
                    .findByIdParaAsignacion(request.getIdProfesor())
                    .orElseThrow(() -> new BusinessException(
                            "El profesor seleccionado no existe."
                    ));

            if (!Boolean.TRUE.equals(profesor.getActivo())) {
                throw new BusinessException(
                        "El profesor seleccionado se encuentra inactivo."
                );
            }
        }

        // 4. Modalidad y estado deben ser de los permitidos.
        if (!MODALIDADES_VALIDAS.contains(modalidad)) {
            throw new BusinessException(
                    "La modalidad indicada no es válida."
            );
        }

        if (!ESTADOS_VALIDOS_CREACION.contains(estado)) {
            throw new BusinessException(
                    "El estado indicado no es válido."
            );
        }

        // En un curso a coordinar, el profesor se asigna por estudiante.
        if ("PUBLICADO".equals(estado) && profesor == null && !curso.esACoordinar()) {
            throw new BusinessException(
                    "Un grupo solo puede publicarse cuando tiene un profesor activo asignado."
            );
        }

        // 5. Fechas del grupo: opcionales, pero coherentes entre sí
        // y dentro del período académico.
        validarFechas(fechaInicio, fechaFin, periodo);

        // 6. El código no se puede repetir dentro del período.
        if (grupoRepository.existsByIdPeriodoAndCodigo(periodo.getId(), codigo)) {
            throw new BusinessException(
                    "Ya existe un grupo con ese código en el período seleccionado."
            );
        }

        // 7. El horario no puede tener bloques inválidos ni traslapes.
        validarHorarios(horariosPedidos);

        // Sin horario (solo en un curso a coordinar) no hay choques que revisar.
        if (profesor != null && !horariosPedidos.isEmpty()) {
            List<ValidacionHorarioProfesorService.BloqueHorario> horariosPropuestos =
                    horariosPedidos.stream()
                            .map(horario -> new ValidacionHorarioProfesorService.BloqueHorario(
                                    horario.getDiaSemana(),
                                    horario.getHoraInicio(),
                                    horario.getHoraFin()
                            ))
                            .toList();

            LocalDate fechaInicioEfectiva = fechaInicio != null
                    ? fechaInicio
                    : periodo.getFechaInicio();
            LocalDate fechaFinEfectiva = fechaFin != null
                    ? fechaFin
                    : periodo.getFechaFin();

            validacionHorarioProfesorService.validarDisponibilidad(
                    profesor.getId(),
                    periodo.getId(),
                    fechaInicioEfectiva,
                    fechaFinEfectiva,
                    null,
                    horariosPropuestos
            );
        }

        Instant ahora = Instant.now();

        // 8. Crear el grupo.
        Long idUsuario = obtenerIdUsuarioAutenticado();

        Grupo grupo = new Grupo();
        grupo.setIdUsuarioUltimaModificacion(idUsuario);
        grupo.setIdCurso(curso.getId());
        grupo.setIdPeriodo(periodo.getId());
        grupo.setIdProfesor(profesor != null ? profesor.getId() : null);
        grupo.setCodigo(codigo);
        grupo.setCupoMaximo(cupoMaximo);
        grupo.setModalidad(modalidad);
        grupo.setAula(aula);
        grupo.setFechaInicio(fechaInicio);
        grupo.setFechaFin(fechaFin);
        grupo.setEstado(estado);
        grupo.setCreadoEn(ahora);
        grupo.setActualizadoEn(ahora);

        Grupo grupoGuardado = grupoRepository.save(grupo);

        // 9. Guardar el horario semanal. Un bloque sin aula propia
        // usa el aula del grupo.
        List<HorarioGrupo> horarios = horariosPedidos
                .stream()
                .map(h -> {
                    HorarioGrupo horario = new HorarioGrupo();
                    horario.setIdGrupo(grupoGuardado.getId());
                    horario.setDiaSemana(h.getDiaSemana());
                    horario.setHoraInicio(h.getHoraInicio());
                    horario.setHoraFin(h.getHoraFin());

                    String aulaHorario = textoOpcional(h.getAula());
                    horario.setAula(aulaHorario != null ? aulaHorario : aula);

                    return horario;
                })
                .toList();

        List<HorarioGrupo> horariosGuardados =
                horarioGrupoRepository.saveAll(horarios);

        return mapearAResponse(
                grupoGuardado,
                curso,
                periodo,
                profesor,
                horariosGuardados
        );
    }

    private void validarFechas(
            LocalDate fechaInicio,
            LocalDate fechaFin,
            PeriodoAcademico periodo
    ) {

        if (fechaInicio != null
                && fechaFin != null
                && fechaFin.isBefore(fechaInicio)) {

            throw new BusinessException(
                    "La fecha de fin del grupo no puede ser anterior a la fecha de inicio."
            );
        }

        if (fechaInicio != null
                && (fechaInicio.isBefore(periodo.getFechaInicio())
                || fechaInicio.isAfter(periodo.getFechaFin()))) {

            throw new BusinessException(
                    "La fecha de inicio del grupo debe estar dentro del período académico."
            );
        }

        if (fechaFin != null
                && (fechaFin.isBefore(periodo.getFechaInicio())
                || fechaFin.isAfter(periodo.getFechaFin()))) {

            throw new BusinessException(
                    "La fecha de fin del grupo debe estar dentro del período académico."
            );
        }
    }

    /*
     * Revisa cada bloque y que dos bloques del mismo día no se traslapen.
     * Ej.: lunes 8:00-10:00 y lunes 9:00-11:00 chocan entre sí.
     */
    private void validarHorarios(List<HorarioGrupoRequestDTO> horarios) {

        for (HorarioGrupoRequestDTO horario : horarios) {

            if (!horario.getHoraFin().isAfter(horario.getHoraInicio())) {
                throw new BusinessException(
                        "En cada horario, la hora de fin debe ser posterior a la hora de inicio."
                );
            }

            if (horario.getHoraInicio().isBefore(HORA_MINIMA)
                    || horario.getHoraFin().isAfter(HORA_MAXIMA)) {
                throw new BusinessException(
                        "Los horarios deben estar entre las 7:00 a. m. y las 10:00 p. m."
                );
            }
        }

        List<HorarioGrupoRequestDTO> ordenados = horarios.stream()
                .sorted(Comparator
                        .comparing(HorarioGrupoRequestDTO::getDiaSemana)
                        .thenComparing(HorarioGrupoRequestDTO::getHoraInicio))
                .toList();

        for (int i = 1; i < ordenados.size(); i++) {

            HorarioGrupoRequestDTO anterior = ordenados.get(i - 1);
            HorarioGrupoRequestDTO actual = ordenados.get(i);

            if (anterior.getDiaSemana().equals(actual.getDiaSemana())
                    && actual.getHoraInicio().isBefore(anterior.getHoraFin())) {

                throw new BusinessException(
                        "Hay horarios que se traslapan el mismo día."
                );
            }
        }
    }

    private Long obtenerIdUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalStateException("No se pudo identificar al usuario autenticado.");
        }

        Usuario usuario = usuarioRepository.findByCedula(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "El usuario autenticado no existe en el sistema."
                ));
        return usuario.getId();
    }

    private String textoOpcional(String texto) {

        if (texto == null || texto.isBlank()) {
            return null;
        }

        return texto.trim();
    }

    private GrupoResponseDTO mapearAResponse(
            Grupo grupo,
            Curso curso,
            PeriodoAcademico periodo,
            Profesor profesor,
            List<HorarioGrupo> horarios
    ) {

        String nombreProfesor = profesor == null
                ? null
                : String.join(
                        " ",
                        profesor.getNombres(),
                        profesor.getPrimerApellido(),
                        profesor.getSegundoApellido() != null
                                ? profesor.getSegundoApellido()
                                : ""
                ).trim().replaceAll("\\s+", " ");

        List<GrupoResponseDTO.Horario> horariosResponse = horarios.stream()
                .sorted(Comparator
                        .comparing(HorarioGrupo::getDiaSemana)
                        .thenComparing(HorarioGrupo::getHoraInicio))
                .map(h -> new GrupoResponseDTO.Horario(
                        h.getId(),
                        h.getDiaSemana(),
                        h.getHoraInicio(),
                        h.getHoraFin(),
                        h.getAula()
                ))
                .toList();

        return new GrupoResponseDTO(
                grupo.getId(),
                grupo.getCodigo(),
                curso.getId(),
                curso.getNombre(),
                periodo.getId(),
                periodo.getNombre(),
                grupo.getIdProfesor(),
                nombreProfesor,
                CupoGrupo.esSinCupo(grupo, curso) ? null : grupo.getCupoMaximo(),
                grupo.getModalidad(),
                grupo.getAula(),
                grupo.getFechaInicio(),
                grupo.getFechaFin(),
                grupo.getEstado(),
                horariosResponse,
                CupoGrupo.esSinCupo(grupo, curso)
        );
    }
}
