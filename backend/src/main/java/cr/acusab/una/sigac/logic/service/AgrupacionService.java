package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Agrupacion;
import cr.acusab.una.sigac.data.entity.AgrupacionInstrumento;
import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.GrupoProfesor;
import cr.acusab.una.sigac.data.entity.HorarioGrupo;
import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.AgrupacionInstrumentoRepository;
import cr.acusab.una.sigac.data.repository.AgrupacionRepository;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.GrupoProfesorRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.HorarioGrupoRepository;
import cr.acusab.una.sigac.data.repository.InstrumentoRepository;
import cr.acusab.una.sigac.data.repository.MatriculaDetalleRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.presentation.dto.request.AgrupacionRequest;
import cr.acusab.una.sigac.presentation.dto.request.GrupoAgrupacionRequest;
import cr.acusab.una.sigac.presentation.dto.request.HorarioGrupoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.AgrupacionResponse;
import cr.acusab.una.sigac.presentation.dto.response.AvanceAgrupacionesResponse;
import cr.acusab.una.sigac.presentation.dto.response.EstudianteAgrupacionResponse;
import cr.acusab.una.sigac.presentation.dto.response.GrupoAgrupacionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * Agrupaciones (Orquesta, Banda, Coro de niños...).
 *
 * - Catálogo: nombre, tipo (INSTRUMENTAL o CORAL), instrumentos permitidos
 *   y si está activa.
 * - Grupo de la agrupación en un período (grupos.tipo_grupo = AGRUPACION):
 *   cupo, bloques de horario, profesores (uno responsable) y estado
 *   PLANIFICADO o PUBLICADO. grupos.id_profesor = responsable.
 * - Vistas internas con el nivel de cada estudiante ("Orquesta ·
 *   Conjunto Instrumental V"). El estudiante nunca ve el nivel.
 */
@Service
public class AgrupacionService {

    public static final String PREFIJO_CODIGO_GRUPO = "AGR-";

    private static final String ESTADO_PLANIFICADO = "PLANIFICADO";
    private static final String ESTADO_PUBLICADO = "PUBLICADO";
    private static final Set<String> ESTADOS_EDITABLES = Set.of(ESTADO_PLANIFICADO, ESTADO_PUBLICADO);
    private static final Set<String> ESTADOS_PERIODO_CERRADOS = Set.of("CERRADO", "CANCELADO");
    private static final String ESTADO_MATRICULA_REGISTRADA = "REGISTRADA";
    private static final String MODALIDAD_PRESENCIAL = "PRESENCIAL";

    // Misma franja que los grupos normales y los espacios.
    private static final LocalTime HORA_MINIMA = LocalTime.of(7, 0);
    private static final LocalTime HORA_MAXIMA = LocalTime.of(22, 0);

    private static final String ROL_PROFESOR = "ROLE_PROFESOR";
    private static final Set<String> ROLES_INTERNOS = Set.of("ROLE_DIRECTOR_ACADEMICO", "ROLE_ADMINISTRATIVO");

    private final AgrupacionRepository agrupacionRepository;
    private final AgrupacionInstrumentoRepository agrupacionInstrumentoRepository;
    private final GrupoProfesorRepository grupoProfesorRepository;
    private final GrupoRepository grupoRepository;
    private final HorarioGrupoRepository horarioGrupoRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final InstrumentoRepository instrumentoRepository;
    private final ProfesorRepository profesorRepository;
    private final CursoRepository cursoRepository;
    private final EstudianteRepository estudianteRepository;
    private final MatriculaDetalleRepository matriculaDetalleRepository;
    private final ValidacionHorarioProfesorService validacionHorarioProfesorService;
    private final AvanceAgrupacionService avanceAgrupacionService;
    private final UsuarioRepository usuarioRepository;

    public AgrupacionService(
            AgrupacionRepository agrupacionRepository,
            AgrupacionInstrumentoRepository agrupacionInstrumentoRepository,
            GrupoProfesorRepository grupoProfesorRepository,
            GrupoRepository grupoRepository,
            HorarioGrupoRepository horarioGrupoRepository,
            PeriodoAcademicoRepository periodoRepository,
            InstrumentoRepository instrumentoRepository,
            ProfesorRepository profesorRepository,
            CursoRepository cursoRepository,
            EstudianteRepository estudianteRepository,
            MatriculaDetalleRepository matriculaDetalleRepository,
            ValidacionHorarioProfesorService validacionHorarioProfesorService,
            AvanceAgrupacionService avanceAgrupacionService,
            UsuarioRepository usuarioRepository
    ) {
        this.agrupacionRepository = agrupacionRepository;
        this.agrupacionInstrumentoRepository = agrupacionInstrumentoRepository;
        this.grupoProfesorRepository = grupoProfesorRepository;
        this.grupoRepository = grupoRepository;
        this.horarioGrupoRepository = horarioGrupoRepository;
        this.periodoRepository = periodoRepository;
        this.instrumentoRepository = instrumentoRepository;
        this.profesorRepository = profesorRepository;
        this.cursoRepository = cursoRepository;
        this.estudianteRepository = estudianteRepository;
        this.matriculaDetalleRepository = matriculaDetalleRepository;
        this.validacionHorarioProfesorService = validacionHorarioProfesorService;
        this.avanceAgrupacionService = avanceAgrupacionService;
        this.usuarioRepository = usuarioRepository;
    }

    /* =================================================================
     * Catálogo
     * ================================================================= */

    @Transactional(readOnly = true)
    public List<AgrupacionResponse> listar() {
        return mapearAgrupaciones(agrupacionRepository.findAllByOrderByNombreAsc());
    }

    @Transactional(readOnly = true)
    public AgrupacionResponse obtener(Long idAgrupacion) {
        return mapearAgrupaciones(List.of(buscarAgrupacion(idAgrupacion))).get(0);
    }

    @Transactional
    public AgrupacionResponse crear(AgrupacionRequest request) {

        String codigo = normalizarCodigo(request.getCodigo());
        String nombre = request.getNombre().trim();

        if (agrupacionRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new BusinessException("Ya existe una agrupación con el código " + codigo + ".");
        }
        if (agrupacionRepository.existsByNombreIgnoreCase(nombre)) {
            throw new BusinessException("Ya existe una agrupación llamada «" + nombre + "».");
        }

        List<Integer> instrumentos = validarInstrumentos(request.getIdsInstrumento());
        validarEdades(request.getEdadMinima(), request.getEdadMaxima());

        Agrupacion agrupacion = new Agrupacion();
        agrupacion.setSoloInclusion(Boolean.TRUE.equals(request.getSoloInclusion()));
        agrupacion.setEdadMinima(request.getEdadMinima());
        agrupacion.setEdadMaxima(request.getEdadMaxima());
        agrupacion.setCodigo(codigo);
        agrupacion.setNombre(nombre);
        agrupacion.setTipo(request.getTipo());
        agrupacion.setDescripcion(textoOpcional(request.getDescripcion()));
        agrupacion.setActiva(request.getActiva() == null || request.getActiva());
        agrupacion.setIdUsuarioUltimaModificacion(obtenerIdUsuarioAutenticado());

        Agrupacion guardada = agrupacionRepository.save(agrupacion);
        reemplazarInstrumentos(guardada.getId(), instrumentos);

        return obtener(guardada.getId());
    }

    @Transactional
    public AgrupacionResponse actualizar(Long idAgrupacion, AgrupacionRequest request) {

        Agrupacion agrupacion = buscarAgrupacion(idAgrupacion);

        String codigo = normalizarCodigo(request.getCodigo());
        String nombre = request.getNombre().trim();

        if (agrupacionRepository.existsByCodigoIgnoreCaseAndIdNot(codigo, idAgrupacion)) {
            throw new BusinessException("Ya existe otra agrupación con el código " + codigo + ".");
        }
        if (agrupacionRepository.existsByNombreIgnoreCaseAndIdNot(nombre, idAgrupacion)) {
            throw new BusinessException("Ya existe otra agrupación llamada «" + nombre + "».");
        }

        boolean tieneGrupos = grupoRepository.existsByIdAgrupacion(idAgrupacion);

        // Cambiar el tipo cambiaría la familia (CI o Coro) de los niveles ya cursados.
        if (tieneGrupos && !agrupacion.getTipo().equals(request.getTipo())) {
            throw new BusinessException(
                    "No se puede cambiar el tipo de «" + agrupacion.getNombre()
                            + "»: ya tiene grupos y sus estudiantes tienen niveles de "
                            + FamiliaAgrupacion.de(agrupacion).nombre() + ".");
        }

        // El código del grupo (AGR-<código>-n) ya está en los grupos creados.
        if (tieneGrupos && !agrupacion.getCodigo().equalsIgnoreCase(codigo)) {
            throw new BusinessException(
                    "No se puede cambiar el código de «" + agrupacion.getNombre() + "»: ya tiene grupos con ese código.");
        }

        List<Integer> instrumentos = validarInstrumentos(request.getIdsInstrumento());
        validarEdades(request.getEdadMinima(), request.getEdadMaxima());

        agrupacion.setSoloInclusion(Boolean.TRUE.equals(request.getSoloInclusion()));
        agrupacion.setEdadMinima(request.getEdadMinima());
        agrupacion.setEdadMaxima(request.getEdadMaxima());
        agrupacion.setCodigo(codigo);
        agrupacion.setNombre(nombre);
        agrupacion.setTipo(request.getTipo());
        agrupacion.setDescripcion(textoOpcional(request.getDescripcion()));
        if (request.getActiva() != null) {
            agrupacion.setActiva(request.getActiva());
        }
        agrupacion.setIdUsuarioUltimaModificacion(obtenerIdUsuarioAutenticado());
        agrupacionRepository.save(agrupacion);

        reemplazarInstrumentos(idAgrupacion, instrumentos);

        return obtener(idAgrupacion);
    }

    private List<Integer> validarInstrumentos(List<Integer> ids) {

        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<Integer> distintos = ids.stream().filter(Objects::nonNull).distinct().toList();

        List<Instrumento> encontrados = instrumentoRepository.findAllById(distintos);

        if (encontrados.size() != distintos.size()) {
            throw new BusinessException("Uno o más de los instrumentos seleccionados no existen.");
        }

        return distintos;
    }

    private static void validarEdades(Integer edadMinima, Integer edadMaxima) {
        String error = ReglasEdad.errorDeLimites(edadMinima, edadMaxima);
        if (error != null) {
            throw new BusinessException(error);
        }
    }

    private void reemplazarInstrumentos(Long idAgrupacion, List<Integer> idsInstrumento) {
        agrupacionInstrumentoRepository.eliminarPorAgrupacion(idAgrupacion);
        agrupacionInstrumentoRepository.saveAll(idsInstrumento.stream()
                .map(id -> new AgrupacionInstrumento(idAgrupacion, id))
                .toList());
    }

    /* =================================================================
     * Grupos de una agrupación en un período
     * ================================================================= */

    @Transactional(readOnly = true)
    public List<GrupoAgrupacionResponse> listarGrupos(Long idPeriodo) {
        return mapearGrupos(grupoRepository.findGruposAgrupacion(idPeriodo), Map.of());
    }

    @Transactional(readOnly = true)
    public GrupoAgrupacionResponse obtenerGrupo(Long idGrupo) {
        return mapearGrupos(List.of(buscarGrupo(idGrupo)), Map.of()).get(0);
    }

    /*
     * Avisos de choque del horario propuesto con los grupos y espacios de
     * sus profesores, sin guardar nada (para el aviso amarillo del
     * formulario mientras se edita).
     */
    @Transactional(readOnly = true)
    public List<GrupoAgrupacionResponse.Choque> revisarChoques(GrupoAgrupacionRequest request, Long idGrupoExcluido) {
        PeriodoAcademico periodo = periodoRepository.findById(request.getIdPeriodo())
                .orElseThrow(() -> new BusinessException("El período académico seleccionado no existe."));
        List<HorarioGrupoRequestDTO> bloques = request.getBloques() == null ? List.of() : request.getBloques();
        List<Long> idsProfesor = request.getProfesores() == null ? List.of()
                : request.getProfesores().stream().map(GrupoAgrupacionRequest.ProfesorGrupo::getIdProfesor)
                .filter(Objects::nonNull).distinct().toList();
        return buscarChoques(idsProfesor, periodo, idGrupoExcluido, bloques);
    }

    @Transactional
    public GrupoAgrupacionResponse crearGrupo(GrupoAgrupacionRequest request) {

        Agrupacion agrupacion = buscarAgrupacion(request.getIdAgrupacion());

        if (!Boolean.TRUE.equals(agrupacion.getActiva())) {
            throw new BusinessException("La agrupación «" + agrupacion.getNombre() + "» está inactiva.");
        }

        PeriodoAcademico periodo = obtenerPeriodoAbierto(request.getIdPeriodo());

        Grupo grupo = new Grupo();
        grupo.setTipoGrupo(Grupo.TIPO_AGRUPACION);
        grupo.setIdAgrupacion(agrupacion.getId());
        grupo.setIdPeriodo(periodo.getId());
        grupo.setIdCurso(null);
        grupo.setIdInstrumento(null);
        grupo.setModalidad(MODALIDAD_PRESENCIAL);
        grupo.setCodigo(siguienteCodigo(agrupacion, periodo.getId()));
        grupo.setCreadoEn(Instant.now());

        return guardarGrupo(grupo, agrupacion, periodo, request, 0L);
    }

    @Transactional
    public GrupoAgrupacionResponse actualizarGrupo(Long idGrupo, GrupoAgrupacionRequest request) {

        Grupo grupo = buscarGrupo(idGrupo);

        if (!ESTADOS_EDITABLES.contains(grupo.getEstado())) {
            throw new BusinessException("Solo se pueden modificar las agrupaciones planificadas o publicadas.");
        }

        long matriculados = contar(matriculaDetalleRepository.contarCuposOcupados(
                List.of(idGrupo), ESTADO_MATRICULA_REGISTRADA)).getOrDefault(idGrupo, 0L);
        long historial = contar(matriculaDetalleRepository.contarDetallesPorGrupo(List.of(idGrupo)))
                .getOrDefault(idGrupo, 0L);

        boolean cambiaAgrupacionOPeriodo = !Objects.equals(grupo.getIdAgrupacion(), request.getIdAgrupacion())
                || !Objects.equals(grupo.getIdPeriodo(), request.getIdPeriodo());

        if (historial > 0 && cambiaAgrupacionOPeriodo) {
            throw new BusinessException(
                    "No se puede cambiar la agrupación ni el período: el grupo ya tiene matrículas.");
        }

        if (request.getCupoMaximo() < matriculados) {
            throw new BusinessException("El cupo no puede ser menor que los " + matriculados
                    + " estudiantes ya matriculados.");
        }

        if (matriculados > 0 && ESTADO_PLANIFICADO.equals(request.getEstado())) {
            throw new BusinessException(
                    "El grupo ya tiene estudiantes matriculados: no puede volver a planificado.");
        }

        Agrupacion agrupacion = buscarAgrupacion(request.getIdAgrupacion());
        PeriodoAcademico periodo = obtenerPeriodoAbierto(request.getIdPeriodo());

        if (cambiaAgrupacionOPeriodo) {
            grupo.setIdAgrupacion(agrupacion.getId());
            grupo.setIdPeriodo(periodo.getId());
            grupo.setCodigo(siguienteCodigo(agrupacion, periodo.getId()));
        }

        return guardarGrupo(grupo, agrupacion, periodo, request, matriculados);
    }

    /*
     * Valida y guarda el grupo, su horario y sus profesores. Devuelve los
     * choques de horario como aviso (no bloquean).
     */
    private GrupoAgrupacionResponse guardarGrupo(
            Grupo grupo,
            Agrupacion agrupacion,
            PeriodoAcademico periodo,
            GrupoAgrupacionRequest request,
            long matriculados
    ) {

        List<HorarioGrupoRequestDTO> bloques = request.getBloques() == null ? List.of() : request.getBloques();
        validarBloques(bloques);

        List<GrupoAgrupacionRequest.ProfesorGrupo> profesores =
                request.getProfesores() == null ? List.of() : request.getProfesores();
        Map<Long, Profesor> profesoresPorId = validarProfesores(profesores, request.getEstado());

        Long idResponsable = profesores.stream()
                .filter(GrupoAgrupacionRequest.ProfesorGrupo::isResponsable)
                .map(GrupoAgrupacionRequest.ProfesorGrupo::getIdProfesor)
                .findFirst()
                .orElse(null);

        // "Solo para estudiantes de": son de la agrupación (catálogo).
        if (request.getIdsInstrumento() != null) {
            reemplazarInstrumentos(agrupacion.getId(), validarInstrumentos(request.getIdsInstrumento()));
        }

        // "¿Quiénes pueden matricularla?": también es de la agrupación.
        GrupoAgrupacionRequest.QuienesPueden quienes = request.getQuienesPueden();
        if (quienes != null) {
            validarEdades(quienes.getEdadMinima(), quienes.getEdadMaxima());
            agrupacion.setSoloInclusion(quienes.isSoloInclusion());
            agrupacion.setEdadMinima(quienes.getEdadMinima());
            agrupacion.setEdadMaxima(quienes.getEdadMaxima());
            agrupacion.setIdUsuarioUltimaModificacion(obtenerIdUsuarioAutenticado());
            agrupacionRepository.save(agrupacion);
        }

        String aula = textoOpcional(request.getAula());

        grupo.setCupoMaximo(request.getCupoMaximo().shortValue());
        grupo.setAula(aula);
        grupo.setEstado(request.getEstado());
        grupo.setIdProfesor(idResponsable);
        grupo.setIdUsuarioUltimaModificacion(obtenerIdUsuarioAutenticado());
        grupo.setActualizadoEn(Instant.now());

        Grupo guardado = grupoRepository.save(grupo);

        horarioGrupoRepository.eliminarPorGrupo(guardado.getId());
        for (HorarioGrupoRequestDTO bloque : bloques) {
            HorarioGrupo horario = new HorarioGrupo();
            horario.setIdGrupo(guardado.getId());
            horario.setDiaSemana(bloque.getDiaSemana());
            horario.setHoraInicio(bloque.getHoraInicio());
            horario.setHoraFin(bloque.getHoraFin());
            horario.setAula(textoOpcional(bloque.getAula()) != null ? textoOpcional(bloque.getAula()) : aula);
            horarioGrupoRepository.save(horario);
        }

        grupoProfesorRepository.eliminarPorGrupo(guardado.getId());
        grupoProfesorRepository.saveAll(profesores.stream()
                .map(p -> new GrupoProfesor(guardado.getId(), p.getIdProfesor(), p.isResponsable()))
                .toList());

        List<GrupoAgrupacionResponse.Choque> choques =
                buscarChoques(new ArrayList<>(profesoresPorId.keySet()), periodo, guardado.getId(), bloques);

        return mapearGrupos(List.of(guardado), Map.of(guardado.getId(), choques)).get(0);
    }

    private void validarBloques(List<HorarioGrupoRequestDTO> bloques) {

        if (bloques.isEmpty()) {
            throw new BusinessException("Agregue al menos un bloque de horario.");
        }

        for (int i = 0; i < bloques.size(); i++) {
            HorarioGrupoRequestDTO b = bloques.get(i);
            String prefijo = "Bloque " + (i + 1) + ": ";

            if (b == null || b.getDiaSemana() == null || b.getHoraInicio() == null || b.getHoraFin() == null) {
                throw new BusinessException(prefijo + "indique el día, la hora de inicio y la hora de fin.");
            }
            if (!b.getHoraFin().isAfter(b.getHoraInicio())) {
                throw new BusinessException(prefijo + "la hora de fin debe ser posterior a la hora de inicio.");
            }
            if (b.getHoraInicio().isBefore(HORA_MINIMA) || b.getHoraFin().isAfter(HORA_MAXIMA)) {
                throw new BusinessException(prefijo + "el horario debe estar entre las 7:00 a. m. y las 10:00 p. m.");
            }
            for (int j = 0; j < i; j++) {
                HorarioGrupoRequestDTO otro = bloques.get(j);
                if (b.getDiaSemana().equals(otro.getDiaSemana())
                        && b.getHoraInicio().isBefore(otro.getHoraFin())
                        && otro.getHoraInicio().isBefore(b.getHoraFin())) {
                    throw new BusinessException(prefijo + "choca con el bloque " + (j + 1) + " de este mismo grupo.");
                }
            }
        }
    }

    /*
     * Profesores: sin repetir, todos existentes y como máximo un
     * responsable. Si hay profesores, uno debe ser el responsable. Para
     * publicar, el responsable debe existir y estar activo.
     */
    private Map<Long, Profesor> validarProfesores(List<GrupoAgrupacionRequest.ProfesorGrupo> profesores, String estado) {

        List<Long> ids = profesores.stream().map(GrupoAgrupacionRequest.ProfesorGrupo::getIdProfesor).toList();

        if (ids.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException("Hay un profesor sin seleccionar.");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException("Un mismo profesor está agregado dos veces.");
        }

        Map<Long, Profesor> encontrados = porId(profesorRepository.findAllById(ids), Profesor::getId);

        if (encontrados.size() != ids.size()) {
            throw new BusinessException("Uno o más de los profesores seleccionados no existen.");
        }

        long responsables = profesores.stream().filter(GrupoAgrupacionRequest.ProfesorGrupo::isResponsable).count();

        if (responsables > 1) {
            throw new BusinessException("Solo puede haber un profesor responsable.");
        }
        if (!profesores.isEmpty() && responsables == 0) {
            throw new BusinessException("Marque cuál de los profesores es el responsable.");
        }

        if (ESTADO_PUBLICADO.equals(estado)) {
            Profesor responsable = profesores.stream()
                    .filter(GrupoAgrupacionRequest.ProfesorGrupo::isResponsable)
                    .map(p -> encontrados.get(p.getIdProfesor()))
                    .findFirst()
                    .orElse(null);

            if (responsable == null || !Boolean.TRUE.equals(responsable.getActivo())) {
                throw new BusinessException(
                        "Para publicar la agrupación necesita un profesor responsable activo. "
                                + "Puede guardarla como planificada mientras tanto.");
            }
        }

        // Se conserva el orden en que se enviaron.
        Map<Long, Profesor> ordenados = new LinkedHashMap<>();
        ids.forEach(id -> ordenados.put(id, encontrados.get(id)));
        return ordenados;
    }

    /*
     * Choques del horario con los grupos, espacios y otras agrupaciones de
     * cada profesor en ese período (reutiliza la validación de HU-014).
     */
    private List<GrupoAgrupacionResponse.Choque> buscarChoques(
            List<Long> idsProfesor, PeriodoAcademico periodo, Long idGrupoExcluido, List<HorarioGrupoRequestDTO> bloques) {

        if (idsProfesor.isEmpty() || bloques.isEmpty()) {
            return List.of();
        }

        Map<Long, Profesor> profesores = porId(profesorRepository.findAllById(idsProfesor), Profesor::getId);
        List<GrupoAgrupacionResponse.Choque> choques = new ArrayList<>();

        for (Long idProfesor : idsProfesor) {
            Profesor profesor = profesores.get(idProfesor);
            if (profesor == null) {
                continue;
            }
            String nombre = nombreCompleto(profesor.getNombres(), profesor.getPrimerApellido(), profesor.getSegundoApellido());

            for (int i = 0; i < bloques.size(); i++) {
                HorarioGrupoRequestDTO b = bloques.get(i);
                if (b == null || b.getDiaSemana() == null || b.getHoraInicio() == null || b.getHoraFin() == null
                        || !b.getHoraFin().isAfter(b.getHoraInicio())) {
                    continue;
                }
                int indice = i;
                validacionHorarioProfesorService.buscarConflicto(
                        idProfesor, periodo.getId(), periodo.getFechaInicio(), periodo.getFechaFin(), idGrupoExcluido,
                        List.of(new ValidacionHorarioProfesorService.BloqueHorario(
                                b.getDiaSemana(), b.getHoraInicio(), b.getHoraFin()))
                ).ifPresent(conflicto -> {
                    Grupo otro = conflicto.grupo();
                    HorarioGrupo h = conflicto.horario();
                    choques.add(new GrupoAgrupacionResponse.Choque(
                            indice,
                            idProfesor,
                            nombre,
                            otro != null ? otro.getCodigo() : null,
                            nombre + " ya tiene " + descripcionGrupo(otro) + " el "
                                    + FormatoHorario.bloque(h.getDiaSemana(), h.getHoraInicio(), h.getHoraFin()) + "."
                    ));
                });
            }
        }

        return choques;
    }

    // "el grupo SOL-I-A", "el espacio ESP-PIA-12-1" o "la agrupación AGR-ORQ-1".
    static String descripcionGrupo(Grupo grupo) {
        if (grupo == null) {
            return "otro grupo";
        }
        if (grupo.esAgrupacion()) {
            return "la agrupación " + grupo.getCodigo();
        }
        return (grupo.esEspacioInstrumento() ? "el espacio " : "el grupo ") + grupo.getCodigo();
    }

    // AGR-<código de la agrupación>-<n>, el primer n libre del período.
    private String siguienteCodigo(Agrupacion agrupacion, Long idPeriodo) {
        String prefijo = PREFIJO_CODIGO_GRUPO + agrupacion.getCodigo().toUpperCase(Locale.ROOT) + "-";
        int n = 1;
        while (grupoRepository.existsByIdPeriodoAndCodigo(idPeriodo, prefijo + n)) {
            n++;
        }
        return prefijo + n;
    }

    /* =================================================================
     * Vistas internas (con el nivel)
     * ================================================================= */

    /*
     * Estudiantes matriculados en el grupo con su nivel. Dirección y
     * administración ven cualquier grupo; un profesor, solo los suyos.
     */
    @Transactional(readOnly = true)
    public List<EstudianteAgrupacionResponse> estudiantesDelGrupo(Long idGrupo) {

        Grupo grupo = buscarGrupo(idGrupo);
        validarAccesoProfesor(grupo);

        Agrupacion agrupacion = agrupacionRepository.findById(grupo.getIdAgrupacion()).orElse(null);
        String nombreAgrupacion = agrupacion != null ? agrupacion.getNombre() : grupo.getCodigo();

        List<Object[]> ocupantes = matriculaDetalleRepository.findOcupantesDeGrupos(
                List.of(idGrupo), ESTADO_MATRICULA_REGISTRADA);

        if (ocupantes.isEmpty()) {
            return List.of();
        }

        Map<Long, Estudiante> estudiantes = porId(estudianteRepository.findAllById(ocupantes.stream()
                .map(f -> ((Number) f[2]).longValue()).distinct().toList()), Estudiante::getId);

        List<Long> idsNivel = ocupantes.stream().map(f -> f[1]).filter(Objects::nonNull)
                .map(v -> ((Number) v).longValue()).distinct().toList();
        Map<Long, Curso> niveles = idsNivel.isEmpty() ? Map.of() : porId(cursoRepository.findAllById(idsNivel), Curso::getId);

        return ocupantes.stream()
                .map(fila -> {
                    Estudiante e = estudiantes.get(((Number) fila[2]).longValue());
                    Curso nivel = fila[1] == null ? null : niveles.get(((Number) fila[1]).longValue());
                    boolean comoCoro = agrupacion != null && agrupacion.esInstrumental()
                            && FamiliaAgrupacion.CORO.incluye(nivel);
                    return new EstudianteAgrupacionResponse(
                            e != null ? e.getId() : null,
                            e != null ? e.getNumeroIdentificacion() : null,
                            e != null ? nombreCompleto(e.getNombres(), e.getPrimerApellido(), e.getSegundoApellido()) : null,
                            nivel != null ? nivel.getId() : null,
                            nivel != null ? nivel.getNombre() : null,
                            AvanceAgrupacionService.etiquetaInterna(nombreAgrupacion, nivel, comoCoro),
                            comoCoro
                    );
                })
                .sorted(Comparator.comparing(EstudianteAgrupacionResponse::nombreCompleto,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /*
     * ¿El usuario de la sesión puede registrar o modificar las notas del
     * grupo? Dirección académica sí; un profesor, solo si es el responsable
     * (los demás profesores ven el grupo y su lista, pero no califican).
     * SIGAC todavía no tiene la pantalla de calificaciones: cuando se haga,
     * debe llamar a validarPuedeCalificar antes de guardar una nota.
     */
    @Transactional(readOnly = true)
    public boolean puedeCalificar(Long idGrupo) {
        Grupo grupo = buscarGrupo(idGrupo);
        Set<String> roles = rolesDeLaSesion();
        if (roles.contains("ROLE_DIRECTOR_ACADEMICO")) {
            return true;
        }
        if (!roles.contains(ROL_PROFESOR)) {
            return false;
        }
        return Objects.equals(grupo.getIdProfesor(), profesorDeLaSesion().getId());
    }

    public void validarPuedeCalificar(Long idGrupo) {
        if (!puedeCalificar(idGrupo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo el profesor responsable de la agrupación puede registrar o modificar notas.");
        }
    }

    private Set<String> rolesDeLaSesion() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    /*
     * Grupos de agrupación del profesor de la sesión (responsable o no),
     * del período indicado o de todos.
     */
    @Transactional(readOnly = true)
    public List<GrupoAgrupacionResponse> misGrupos(Long idPeriodo) {
        Profesor profesor = profesorDeLaSesion();
        return mapearGrupos(grupoRepository.findGruposAgrupacion(idPeriodo).stream()
                .filter(g -> esProfesorDelGrupo(g, profesor.getId()))
                .toList(), Map.of());
    }

    /*
     * Avance del estudiante en cada agrupación (vista interna): las
     * agrupaciones activas y las que tengan historial, con lo aprobado y
     * el siguiente nivel con su motivo.
     */
    @Transactional(readOnly = true)
    public AvanceAgrupacionesResponse avanceDelEstudiante(Long idEstudiante) {

        if (!estudianteRepository.existsById(idEstudiante)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El estudiante indicado no existe.");
        }

        AvanceAgrupacionService.Historial historial = avanceAgrupacionService.cargarHistorial(idEstudiante);
        Map<Long, PeriodoAcademico> periodos = new HashMap<>();
        List<AvanceAgrupacionesResponse.Avance> avances = new ArrayList<>();

        for (Agrupacion agrupacion : agrupacionRepository.findAllByOrderByNombreAsc()) {

            List<FamiliaAgrupacion> familias = agrupacion.esInstrumental()
                    ? List.of(FamiliaAgrupacion.CONJUNTO_INSTRUMENTAL, FamiliaAgrupacion.CORO)
                    : List.of(FamiliaAgrupacion.CORO);

            for (FamiliaAgrupacion familia : familias) {

                Map<Long, AvanceInstrumentoService.NivelAprobado> aprobados =
                        avanceAgrupacionService.nivelesAprobados(historial, agrupacion.getId(), familia);

                boolean propia = familia == FamiliaAgrupacion.de(agrupacion);

                // El coro de una instrumental (D4) solo aparece si tiene historial.
                if (!propia && aprobados.isEmpty()) {
                    continue;
                }
                if (!Boolean.TRUE.equals(agrupacion.getActiva()) && aprobados.isEmpty()) {
                    continue;
                }

                AvanceAgrupacionService.SiguienteNivel siguiente =
                        avanceAgrupacionService.siguienteNivel(historial, agrupacion, familia);

                avances.add(new AvanceAgrupacionesResponse.Avance(
                        agrupacion.getId(),
                        agrupacion.getNombre(),
                        agrupacion.getTipo(),
                        familia.name(),
                        familia.nombre(),
                        mapearAprobados(historial, aprobados, agrupacion.getNombre(), familia, periodos),
                        siguiente.curso() != null ? siguiente.curso().getId() : null,
                        siguiente.curso() != null ? siguiente.curso().getNombre() : null,
                        siguiente.curso() != null
                                ? AvanceAgrupacionService.etiquetaInterna(agrupacion.getNombre(), siguiente.curso())
                                : null,
                        siguiente.completado(),
                        siguiente.motivo()
                ));
            }
        }

        // Historial sin agrupación (carga inicial): punto de partida de la regla D2.
        for (FamiliaAgrupacion familia : FamiliaAgrupacion.values()) {
            Map<Long, AvanceInstrumentoService.NivelAprobado> sinAgrupacion =
                    avanceAgrupacionService.nivelesSinAgrupacion(historial, familia);
            if (sinAgrupacion.isEmpty()) {
                continue;
            }
            avances.add(new AvanceAgrupacionesResponse.Avance(
                    null, "Sin agrupación (historial de la certificación)", null,
                    familia.name(), familia.nombre(),
                    mapearAprobados(historial, sinAgrupacion, familia.nombre(), familia, periodos),
                    null, null, null, false,
                    "Es el punto de partida en una agrupación donde todavía no tiene avance propio."
            ));
        }

        // D5: cada familia con el avance de su agrupación más adelantada.
        List<AvanceAgrupacionesResponse.Familia> familias = new ArrayList<>();
        Map<Long, Agrupacion> todas = porId(agrupacionRepository.findAllByOrderByNombreAsc(), Agrupacion::getId);
        for (FamiliaAgrupacion familia : FamiliaAgrupacion.values()) {
            AvanceAgrupacionService.AvanceFamilia avance = avanceAgrupacionService.avanceFamilia(historial, familia);
            Agrupacion mejor = avance.idAgrupacion() == null ? null : todas.get(avance.idAgrupacion());
            String nombre = mejor != null ? mejor.getNombre() : null;
            familias.add(new AvanceAgrupacionesResponse.Familia(
                    familia.name(),
                    familia.nombre(),
                    avance.idAgrupacion(),
                    nombre,
                    avance.ultimoNivel(),
                    familia.nivelMaximo(),
                    avance.ultimoNivel() == 0 ? null
                            : AvanceAgrupacionService.etiquetaNivel(nombre != null ? nombre : familia.nombre(), avance.ultimoNivel()),
                    avance.completa()
            ));
        }

        return new AvanceAgrupacionesResponse(idEstudiante, avances, familias);
    }

    private List<AvanceAgrupacionesResponse.NivelAprobado> mapearAprobados(
            AvanceAgrupacionService.Historial historial,
            Map<Long, AvanceInstrumentoService.NivelAprobado> aprobados,
            String nombre,
            FamiliaAgrupacion familia,
            Map<Long, PeriodoAcademico> periodos
    ) {
        Map<Long, AvanceAgrupacionService.Nivel> niveles = historial.niveles(familia).stream()
                .collect(Collectors.toMap(n -> n.curso().getId(), Function.identity()));

        return aprobados.values().stream()
                .filter(a -> niveles.containsKey(a.idCurso()))
                .sorted(Comparator.comparingInt(a -> niveles.get(a.idCurso()).numero()))
                .map(a -> {
                    AvanceAgrupacionService.Nivel nivel = niveles.get(a.idCurso());
                    PeriodoAcademico periodo = a.idPeriodo() == null ? null
                            : periodos.computeIfAbsent(a.idPeriodo(), id -> periodoRepository.findById(id).orElse(null));
                    return new AvanceAgrupacionesResponse.NivelAprobado(
                            a.idCurso(),
                            nivel.curso().getNombre(),
                            AvanceAgrupacionService.etiquetaNivel(nombre, nivel.numero()),
                            a.origen(),
                            a.idPeriodo(),
                            periodo != null ? periodo.getNombre() : null,
                            a.fecha(),
                            a.nota()
                    );
                })
                .toList();
    }

    /* =================================================================
     * Apoyo para la matrícula
     * ================================================================= */

    @Transactional(readOnly = true)
    public Map<Long, Agrupacion> agrupacionesPorId(Collection<Long> ids) {
        List<Long> limpios = ids.stream().filter(Objects::nonNull).distinct().toList();
        return limpios.isEmpty() ? Map.of() : porId(agrupacionRepository.findAllById(limpios), Agrupacion::getId);
    }

    // Instrumentos permitidos por agrupación (sin entrada = cualquier instrumento).
    @Transactional(readOnly = true)
    public Map<Long, List<Instrumento>> instrumentosPermitidos(Collection<Long> idsAgrupacion) {

        List<Long> limpios = idsAgrupacion.stream().filter(Objects::nonNull).distinct().toList();
        if (limpios.isEmpty()) {
            return Map.of();
        }

        List<AgrupacionInstrumento> filas = agrupacionInstrumentoRepository.findByIdAgrupacionIn(limpios);
        if (filas.isEmpty()) {
            return Map.of();
        }

        Map<Integer, Instrumento> instrumentos = porId(instrumentoRepository.findAllById(
                filas.stream().map(AgrupacionInstrumento::getIdInstrumento).distinct().toList()), Instrumento::getId);

        Map<Long, List<Instrumento>> resultado = new HashMap<>();
        for (AgrupacionInstrumento fila : filas) {
            Instrumento instrumento = instrumentos.get(fila.getIdInstrumento());
            if (instrumento != null) {
                resultado.computeIfAbsent(fila.getIdAgrupacion(), id -> new ArrayList<>()).add(instrumento);
            }
        }
        resultado.values().forEach(lista -> lista.sort(Comparator.comparing(Instrumento::getNombre)));
        return resultado;
    }

    // Nombres de los profesores de cada grupo, el responsable primero.
    @Transactional(readOnly = true)
    public Map<Long, List<String>> nombresProfesores(Collection<Grupo> grupos) {
        Map<Long, List<GrupoAgrupacionResponse.Profesor>> profesores = profesoresDeGrupos(grupos);
        Map<Long, List<String>> resultado = new HashMap<>();
        profesores.forEach((idGrupo, lista) ->
                resultado.put(idGrupo, lista.stream().map(GrupoAgrupacionResponse.Profesor::nombre).toList()));
        return resultado;
    }

    /* =================================================================
     * Conversión a respuesta
     * ================================================================= */

    private List<AgrupacionResponse> mapearAgrupaciones(List<Agrupacion> agrupaciones) {

        if (agrupaciones.isEmpty()) {
            return List.of();
        }

        List<Long> ids = agrupaciones.stream().map(Agrupacion::getId).toList();
        Map<Long, List<Instrumento>> instrumentos = instrumentosPermitidos(ids);

        Map<Long, Long> grupos = grupoRepository.findGruposAgrupacion(null).stream()
                .filter(g -> g.getIdAgrupacion() != null)
                .collect(Collectors.groupingBy(Grupo::getIdAgrupacion, Collectors.counting()));

        return agrupaciones.stream()
                .map(a -> new AgrupacionResponse(
                        a.getId(),
                        a.getCodigo(),
                        a.getNombre(),
                        a.getTipo(),
                        a.getDescripcion(),
                        Boolean.TRUE.equals(a.getActiva()),
                        instrumentos.getOrDefault(a.getId(), List.of()).stream()
                                .map(i -> new AgrupacionResponse.Instrumento(i.getId(), i.getNombre()))
                                .toList(),
                        grupos.getOrDefault(a.getId(), 0L),
                        Boolean.TRUE.equals(a.getSoloInclusion()),
                        a.getEdadMinima(),
                        a.getEdadMaxima()
                ))
                .toList();
    }

    private List<GrupoAgrupacionResponse> mapearGrupos(
            List<Grupo> grupos, Map<Long, List<GrupoAgrupacionResponse.Choque>> choques) {

        if (grupos.isEmpty()) {
            return List.of();
        }

        List<Long> ids = grupos.stream().map(Grupo::getId).toList();

        Map<Long, List<HorarioGrupo>> horarios = horarioGrupoRepository
                .findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(ids)
                .stream()
                .collect(Collectors.groupingBy(HorarioGrupo::getIdGrupo));

        Map<Long, PeriodoAcademico> periodos = porId(periodoRepository.findAllById(
                grupos.stream().map(Grupo::getIdPeriodo).distinct().toList()), PeriodoAcademico::getId);

        Map<Long, Agrupacion> agrupaciones = agrupacionesPorId(grupos.stream().map(Grupo::getIdAgrupacion).toList());
        Map<Long, List<Instrumento>> instrumentos = instrumentosPermitidos(agrupaciones.keySet());
        Map<Long, List<GrupoAgrupacionResponse.Profesor>> profesores = profesoresDeGrupos(grupos);
        Map<Long, Long> matriculados = contar(matriculaDetalleRepository.contarCuposOcupados(ids, ESTADO_MATRICULA_REGISTRADA));

        return grupos.stream()
                .map(g -> {
                    Agrupacion a = agrupaciones.get(g.getIdAgrupacion());
                    PeriodoAcademico p = periodos.get(g.getIdPeriodo());
                    long ocupados = matriculados.getOrDefault(g.getId(), 0L);
                    return new GrupoAgrupacionResponse(
                            g.getId(),
                            g.getCodigo(),
                            g.getIdPeriodo(),
                            p != null ? p.getNombre() : null,
                            g.getIdAgrupacion(),
                            a != null ? a.getNombre() : null,
                            a != null ? a.getTipo() : null,
                            g.getCupoMaximo(),
                            ocupados,
                            (int) Math.max(0, g.getCupoMaximo() - ocupados),
                            g.getEstado(),
                            g.getAula(),
                            horarios.getOrDefault(g.getId(), List.of()).stream()
                                    .map(h -> new GrupoAgrupacionResponse.Horario(
                                            h.getDiaSemana(), h.getHoraInicio(), h.getHoraFin(), h.getAula()))
                                    .toList(),
                            profesores.getOrDefault(g.getId(), List.of()),
                            instrumentos.getOrDefault(g.getIdAgrupacion(), List.of()).stream()
                                    .map(i -> new AgrupacionResponse.Instrumento(i.getId(), i.getNombre()))
                                    .toList(),
                            choques.getOrDefault(g.getId(), List.of()),
                            a != null && Boolean.TRUE.equals(a.getSoloInclusion()),
                            a != null ? a.getEdadMinima() : null,
                            a != null ? a.getEdadMaxima() : null
                    );
                })
                .toList();
    }

    /*
     * Profesores de cada grupo (grupos_profesores), el responsable primero.
     * Si el grupo no tiene filas pero sí grupos.id_profesor, ese es el
     * responsable.
     */
    private Map<Long, List<GrupoAgrupacionResponse.Profesor>> profesoresDeGrupos(Collection<Grupo> grupos) {

        if (grupos.isEmpty()) {
            return Map.of();
        }

        List<GrupoProfesor> filas = grupoProfesorRepository.findByIdGrupoIn(grupos.stream().map(Grupo::getId).toList());

        Set<Long> idsProfesor = new LinkedHashSet<>();
        filas.forEach(f -> idsProfesor.add(f.getIdProfesor()));
        grupos.forEach(g -> {
            if (g.getIdProfesor() != null) idsProfesor.add(g.getIdProfesor());
        });

        Map<Long, Profesor> profesores = idsProfesor.isEmpty() ? Map.of()
                : porId(profesorRepository.findAllById(idsProfesor), Profesor::getId);

        Map<Long, List<GrupoProfesor>> porGrupo = filas.stream().collect(Collectors.groupingBy(GrupoProfesor::getIdGrupo));

        Map<Long, List<GrupoAgrupacionResponse.Profesor>> resultado = new HashMap<>();

        for (Grupo grupo : grupos) {
            List<GrupoProfesor> delGrupo = new ArrayList<>(porGrupo.getOrDefault(grupo.getId(), List.of()));
            if (delGrupo.isEmpty() && grupo.getIdProfesor() != null) {
                delGrupo.add(new GrupoProfesor(grupo.getId(), grupo.getIdProfesor(), true));
            }
            resultado.put(grupo.getId(), delGrupo.stream()
                    .sorted(Comparator.comparing((GrupoProfesor gp) -> !Boolean.TRUE.equals(gp.getEsResponsable())))
                    .map(gp -> {
                        Profesor p = profesores.get(gp.getIdProfesor());
                        return new GrupoAgrupacionResponse.Profesor(
                                gp.getIdProfesor(),
                                p != null ? nombreCompleto(p.getNombres(), p.getPrimerApellido(), p.getSegundoApellido()) : null,
                                Boolean.TRUE.equals(gp.getEsResponsable()),
                                p != null && Boolean.TRUE.equals(p.getActivo())
                        );
                    })
                    .toList());
        }

        return resultado;
    }

    /* =================================================================
     * Búsquedas y sesión
     * ================================================================= */

    private Agrupacion buscarAgrupacion(Long idAgrupacion) {
        return agrupacionRepository.findById(idAgrupacion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "La agrupación indicada no existe."));
    }

    private Grupo buscarGrupo(Long idGrupo) {
        Grupo grupo = grupoRepository.findById(idGrupo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El grupo indicado no existe."));
        if (!grupo.esAgrupacion()) {
            throw new BusinessException("El grupo indicado no es de una agrupación.");
        }
        return grupo;
    }

    private PeriodoAcademico obtenerPeriodoAbierto(Long idPeriodo) {
        PeriodoAcademico periodo = periodoRepository.findById(idPeriodo)
                .orElseThrow(() -> new BusinessException("El período académico seleccionado no existe."));
        if (ESTADOS_PERIODO_CERRADOS.contains(periodo.getEstado())) {
            throw new BusinessException("No se pueden crear ni modificar agrupaciones en un período cerrado o cancelado.");
        }
        return periodo;
    }

    private boolean esProfesorDelGrupo(Grupo grupo, Long idProfesor) {
        return Objects.equals(grupo.getIdProfesor(), idProfesor)
                || grupoProfesorRepository.existsByIdGrupoAndIdProfesor(grupo.getId(), idProfesor);
    }

    // Un profesor (sin rol de dirección o administración) solo ve sus grupos.
    private void validarAccesoProfesor(Grupo grupo) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return;
        }
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        if (roles.stream().anyMatch(ROLES_INTERNOS::contains) || !roles.contains(ROL_PROFESOR)) {
            return;
        }
        Profesor profesor = profesorDeLaSesion();
        if (!esProfesorDelGrupo(grupo, profesor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ese grupo no es suyo.");
        }
    }

    private Profesor profesorDeLaSesion() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException("No se pudo identificar al usuario autenticado.");
        }
        return profesorRepository.findByIdUsuario_Cedula(authentication.getName())
                .orElseThrow(() -> new BusinessException("El usuario autenticado no está asociado a un profesor."));
    }

    private Long obtenerIdUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalStateException("No se pudo identificar al usuario autenticado.");
        }
        return usuarioRepository.findByCedula(authentication.getName())
                .map(Usuario::getId)
                .orElseThrow(() -> new IllegalStateException("El usuario autenticado no existe en el sistema."));
    }

    /* =================================================================
     * Utilidades
     * ================================================================= */

    private static String normalizarCodigo(String codigo) {
        return codigo.trim().toUpperCase(Locale.ROOT);
    }

    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static <K, T> Map<K, T> porId(List<T> lista, Function<T, K> id) {
        return lista.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }

    private static Map<Long, Long> contar(List<Object[]> filas) {
        Map<Long, Long> mapa = new HashMap<>();
        for (Object[] fila : filas) {
            mapa.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
        }
        return mapa;
    }

    static String nombreCompleto(String nombres, String primerApellido, String segundoApellido) {
        return Stream.of(nombres, primerApellido, segundoApellido)
                .filter(p -> p != null && !p.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }
}
