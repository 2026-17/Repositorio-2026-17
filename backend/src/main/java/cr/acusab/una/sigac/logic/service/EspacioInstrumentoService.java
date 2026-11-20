package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Estudiante;
import cr.acusab.una.sigac.data.entity.Grupo;
import cr.acusab.una.sigac.data.entity.HorarioGrupo;
import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.entity.Profesor;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.EstudianteRepository;
import cr.acusab.una.sigac.data.repository.GrupoRepository;
import cr.acusab.una.sigac.data.repository.HorarioGrupoRepository;
import cr.acusab.una.sigac.data.repository.InstrumentoRepository;
import cr.acusab.una.sigac.data.repository.MatriculaDetalleRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import cr.acusab.una.sigac.data.repository.ProfesorRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.exception.BusinessException;
import cr.acusab.una.sigac.logic.exception.EspaciosInvalidosException;
import cr.acusab.una.sigac.presentation.dto.request.EspaciosInstrumentoRequest;
import cr.acusab.una.sigac.presentation.dto.request.HorarioGrupoRequestDTO;
import cr.acusab.una.sigac.presentation.dto.response.EspacioInstrumentoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * Espacios de instrumento.
 *
 * Las lecciones de instrumento son individuales: cada espacio es una hora
 * semanal de un profesor en un instrumento (grupo ESPACIO_INSTRUMENTO, cupo
 * 1, sin curso). El nivel (Instrumento I..XIV) no es del espacio: lo trae
 * el estudiante que lo toma al matricular. Así, el lunes a las 7:10 a. m.
 * puede ser Instrumento I para un estudiante e Instrumento V para otro, sin
 * crear un grupo por nivel.
 */
@Service
public class EspacioInstrumentoService {

    public static final short CUPO_ESPACIO = 1;

    private static final String ESTADO_PUBLICADO = "PUBLICADO";
    private static final String MODALIDAD_PRESENCIAL = "PRESENCIAL";
    private static final String ESTADO_MATRICULA_REGISTRADA = "REGISTRADA";

    private static final Set<String> ESTADOS_PERIODO_CERRADOS = Set.of("CERRADO", "CANCELADO");

    // Misma franja que los grupos normales.
    private static final LocalTime HORA_MINIMA = LocalTime.of(7, 0);
    private static final LocalTime HORA_MAXIMA = LocalTime.of(22, 0);

    private final GrupoRepository grupoRepository;
    private final HorarioGrupoRepository horarioGrupoRepository;
    private final PeriodoAcademicoRepository periodoRepository;
    private final InstrumentoRepository instrumentoRepository;
    private final ProfesorRepository profesorRepository;
    private final CursoRepository cursoRepository;
    private final EstudianteRepository estudianteRepository;
    private final MatriculaDetalleRepository matriculaDetalleRepository;
    private final ValidacionHorarioProfesorService validacionHorarioProfesorService;
    private final UsuarioRepository usuarioRepository;

    public EspacioInstrumentoService(
            GrupoRepository grupoRepository,
            HorarioGrupoRepository horarioGrupoRepository,
            PeriodoAcademicoRepository periodoRepository,
            InstrumentoRepository instrumentoRepository,
            ProfesorRepository profesorRepository,
            CursoRepository cursoRepository,
            EstudianteRepository estudianteRepository,
            MatriculaDetalleRepository matriculaDetalleRepository,
            ValidacionHorarioProfesorService validacionHorarioProfesorService,
            UsuarioRepository usuarioRepository
    ) {
        this.grupoRepository = grupoRepository;
        this.horarioGrupoRepository = horarioGrupoRepository;
        this.periodoRepository = periodoRepository;
        this.instrumentoRepository = instrumentoRepository;
        this.profesorRepository = profesorRepository;
        this.cursoRepository = cursoRepository;
        this.estudianteRepository = estudianteRepository;
        this.matriculaDetalleRepository = matriculaDetalleRepository;
        this.validacionHorarioProfesorService = validacionHorarioProfesorService;
        this.usuarioRepository = usuarioRepository;
    }

    /* =================================================================
     * Crear en lote
     * ================================================================= */

    /*
     * Crea un espacio por bloque. Todo o nada: si algún bloque falla no se
     * crea ninguno y la respuesta dice cuál bloque y por qué.
     */
    @Transactional
    public List<EspacioInstrumentoResponse> crear(EspaciosInstrumentoRequest request) {

        if (request.getCupoMaximo() != null && request.getCupoMaximo() != CUPO_ESPACIO) {
            throw new BusinessException(
                    "El cupo de un espacio de instrumento siempre es 1: las lecciones de instrumento son individuales."
            );
        }

        List<HorarioGrupoRequestDTO> bloques = request.getBloques() == null ? List.of() : request.getBloques();
        if (bloques.isEmpty()) {
            throw new BusinessException("Agregue al menos un espacio.");
        }

        PeriodoAcademico periodo = obtenerPeriodoAbierto(request.getIdPeriodo());
        Instrumento instrumento = obtenerInstrumento(request.getIdInstrumento());
        Profesor profesor = obtenerProfesorConEspecialidad(request.getIdProfesor(), instrumento);
        String aulaPorDefecto = textoOpcional(request.getAula());

        // 1. Revisar cada bloque y juntar todos los errores.
        Map<Integer, String> errores = new HashMap<>();

        for (int i = 0; i < bloques.size(); i++) {
            String error = errorDeHoras(bloques.get(i));
            if (error != null) {
                errores.put(i, error);
            }
        }

        // 2. Choques dentro del mismo lote.
        for (int i = 0; i < bloques.size(); i++) {
            for (int j = 0; j < i; j++) {
                if (errores.containsKey(i) || errores.containsKey(j)) {
                    continue;
                }
                HorarioGrupoRequestDTO a = bloques.get(i);
                HorarioGrupoRequestDTO b = bloques.get(j);
                if (seTraslapan(a, b)) {
                    errores.put(i, "Choca con el espacio " + (j + 1) + " de esta misma lista ("
                            + FormatoHorario.bloque(b.getDiaSemana(), b.getHoraInicio(), b.getHoraFin()) + ").");
                }
            }
        }

        // 3. Choques con otros grupos o espacios del profesor.
        for (int i = 0; i < bloques.size(); i++) {
            if (errores.containsKey(i)) {
                continue;
            }
            int indice = i;
            buscarChoqueConProfesor(profesor, periodo, null, bloques.get(i))
                    .ifPresent(mensaje -> errores.put(indice, mensaje));
        }

        if (!errores.isEmpty()) {
            throw new EspaciosInvalidosException(
                    errores.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(e -> new EspaciosInvalidosException.ErrorBloque(e.getKey(), e.getValue()))
                            .toList()
            );
        }

        // 4. Crear los espacios.
        Long idUsuario = obtenerIdUsuarioAutenticado();
        Instant ahora = Instant.now();
        String prefijo = prefijoCodigo(instrumento, profesor);
        Set<String> codigosUsados = new HashSet<>();
        int siguiente = 1;

        List<Grupo> creados = new ArrayList<>();

        for (HorarioGrupoRequestDTO bloque : bloques) {

            String codigo;
            do {
                codigo = prefijo + siguiente++;
            } while (codigosUsados.contains(codigo)
                    || grupoRepository.existsByIdPeriodoAndCodigo(periodo.getId(), codigo));
            codigosUsados.add(codigo);

            String aula = textoOpcional(bloque.getAula()) != null ? textoOpcional(bloque.getAula()) : aulaPorDefecto;

            Grupo espacio = new Grupo();
            espacio.setTipoGrupo(Grupo.TIPO_ESPACIO_INSTRUMENTO);
            espacio.setIdPeriodo(periodo.getId());
            espacio.setIdCurso(null);
            espacio.setIdInstrumento(instrumento.getId());
            espacio.setIdProfesor(profesor.getId());
            espacio.setIdUsuarioUltimaModificacion(idUsuario);
            espacio.setCodigo(codigo);
            espacio.setCupoMaximo(CUPO_ESPACIO);
            espacio.setModalidad(MODALIDAD_PRESENCIAL);
            espacio.setAula(aula);
            espacio.setEstado(ESTADO_PUBLICADO);
            espacio.setCreadoEn(ahora);
            espacio.setActualizadoEn(ahora);

            Grupo guardado = grupoRepository.save(espacio);

            HorarioGrupo horario = new HorarioGrupo();
            horario.setIdGrupo(guardado.getId());
            horario.setDiaSemana(bloque.getDiaSemana());
            horario.setHoraInicio(bloque.getHoraInicio());
            horario.setHoraFin(bloque.getHoraFin());
            horario.setAula(aula);
            horarioGrupoRepository.save(horario);

            creados.add(guardado);
        }

        return mapear(creados);
    }

    /* =================================================================
     * Agenda del profesor
     * ================================================================= */

    @Transactional(readOnly = true)
    public List<EspacioInstrumentoResponse> listar(Long idPeriodo, Integer idInstrumento, Long idProfesor) {
        return mapear(grupoRepository.findEspacios(idPeriodo, idInstrumento, idProfesor));
    }

    /* =================================================================
     * Modificar y eliminar (solo si el espacio está libre)
     * ================================================================= */

    @Transactional
    public EspacioInstrumentoResponse actualizar(Long idGrupo, HorarioGrupoRequestDTO bloque) {

        Grupo espacio = obtenerEspacio(idGrupo);
        validarLibre(espacio, "modificar");

        String error = errorDeHoras(bloque);
        if (error != null) {
            throw new BusinessException(error);
        }

        PeriodoAcademico periodo = obtenerPeriodoAbierto(espacio.getIdPeriodo());
        Profesor profesor = profesorRepository.findById(espacio.getIdProfesor())
                .orElseThrow(() -> new BusinessException("El profesor del espacio no existe."));

        buscarChoqueConProfesor(profesor, periodo, espacio.getId(), bloque)
                .ifPresent(mensaje -> {
                    throw new BusinessException(mensaje);
                });

        String aula = textoOpcional(bloque.getAula());

        espacio.setAula(aula);
        espacio.setIdUsuarioUltimaModificacion(obtenerIdUsuarioAutenticado());
        espacio.setActualizadoEn(Instant.now());
        Grupo guardado = grupoRepository.save(espacio);

        horarioGrupoRepository.eliminarPorGrupo(idGrupo);

        HorarioGrupo horario = new HorarioGrupo();
        horario.setIdGrupo(idGrupo);
        horario.setDiaSemana(bloque.getDiaSemana());
        horario.setHoraInicio(bloque.getHoraInicio());
        horario.setHoraFin(bloque.getHoraFin());
        horario.setAula(aula);
        horarioGrupoRepository.save(horario);

        return mapear(List.of(guardado)).get(0);
    }

    @Transactional
    public void eliminar(Long idGrupo) {

        Grupo espacio = obtenerEspacio(idGrupo);
        validarLibre(espacio, "eliminar");

        List<Long> ids = List.of(idGrupo);

        // Un espacio que tuvo matrículas (aunque se retiraran) o clases se
        // conserva por el historial académico.
        if (contar(matriculaDetalleRepository.contarDetallesPorGrupo(ids)).getOrDefault(idGrupo, 0L) > 0
                || contar(grupoRepository.contarSesionesPorGrupo(ids)).getOrDefault(idGrupo, 0L) > 0
                || contar(grupoRepository.contarAvancesPorGrupo(ids)).getOrDefault(idGrupo, 0L) > 0) {
            throw new BusinessException(
                    "El espacio tiene historial de matrículas o clases y no se puede eliminar."
            );
        }

        // El trigger de bitácora del DELETE usa id_usuario_ultima_modificacion.
        espacio.setIdUsuarioUltimaModificacion(obtenerIdUsuarioAutenticado());
        grupoRepository.saveAndFlush(espacio);
        grupoRepository.delete(espacio);
    }

    private void validarLibre(Grupo espacio, String accion) {
        List<Object[]> ocupantes = matriculaDetalleRepository.findOcupantesDeGrupos(
                List.of(espacio.getId()), ESTADO_MATRICULA_REGISTRADA);

        if (!ocupantes.isEmpty()) {
            Long idEstudiante = ((Number) ocupantes.get(0)[2]).longValue();
            String estudiante = estudianteRepository.findById(idEstudiante)
                    .map(e -> nombreCompleto(e.getNombres(), e.getPrimerApellido(), e.getSegundoApellido()))
                    .orElse("un estudiante");
            throw new BusinessException(
                    "No se puede " + accion + " el espacio " + espacio.getCodigo() + ": ya lo tomó "
                            + estudiante + " en su matrícula. Solo se pueden " + accion
                            + " los espacios libres."
            );
        }
    }

    /* =================================================================
     * Validaciones
     * ================================================================= */

    private PeriodoAcademico obtenerPeriodoAbierto(Long idPeriodo) {
        PeriodoAcademico periodo = periodoRepository.findById(idPeriodo)
                .orElseThrow(() -> new BusinessException("El período académico seleccionado no existe."));

        if (ESTADOS_PERIODO_CERRADOS.contains(periodo.getEstado())) {
            throw new BusinessException("No se pueden crear ni modificar espacios en un período cerrado o cancelado.");
        }
        return periodo;
    }

    private Instrumento obtenerInstrumento(Integer idInstrumento) {
        return instrumentoRepository.findByIdAndActivoTrue(idInstrumento)
                .orElseThrow(() -> new BusinessException("El instrumento indicado no existe o está inactivo."));
    }

    /*
     * El profesor debe estar activo y tener el instrumento como especialidad
     * (texto separado por comas, igual que en Asignar instrumento).
     */
    private Profesor obtenerProfesorConEspecialidad(Long idProfesor, Instrumento instrumento) {
        Profesor profesor = profesorRepository.findById(idProfesor)
                .orElseThrow(() -> new BusinessException("El profesor seleccionado no existe."));

        if (!Boolean.TRUE.equals(profesor.getActivo())) {
            throw new BusinessException("El profesor seleccionado no está activo.");
        }

        if (!tieneEspecialidad(profesor, instrumento)) {
            throw new BusinessException(
                    "El profesor seleccionado no tiene " + instrumento.getNombre() + " como especialidad."
            );
        }
        return profesor;
    }

    static boolean tieneEspecialidad(Profesor profesor, Instrumento instrumento) {
        String especialidad = profesor.getEspecialidad();
        return especialidad != null
                && especialidad.toLowerCase(Locale.ROOT).contains(instrumento.getNombre().toLowerCase(Locale.ROOT));
    }

    private static String errorDeHoras(HorarioGrupoRequestDTO bloque) {
        if (bloque == null || bloque.getDiaSemana() == null
                || bloque.getHoraInicio() == null || bloque.getHoraFin() == null) {
            return "Indique el día, la hora de inicio y la hora de fin.";
        }
        if (bloque.getDiaSemana() < 1 || bloque.getDiaSemana() > 7) {
            return "El día indicado no es válido.";
        }
        if (!bloque.getHoraFin().isAfter(bloque.getHoraInicio())) {
            return "La hora de fin debe ser posterior a la hora de inicio.";
        }
        if (bloque.getHoraInicio().isBefore(HORA_MINIMA) || bloque.getHoraFin().isAfter(HORA_MAXIMA)) {
            return "Los espacios deben estar entre las 7:00 a. m. y las 10:00 p. m.";
        }
        return null;
    }

    private static boolean seTraslapan(HorarioGrupoRequestDTO a, HorarioGrupoRequestDTO b) {
        return a.getDiaSemana().equals(b.getDiaSemana())
                && a.getHoraInicio().isBefore(b.getHoraFin())
                && b.getHoraInicio().isBefore(a.getHoraFin());
    }

    // Choque con otros grupos o espacios del profesor (reutiliza la validación de HU-014).
    private Optional<String> buscarChoqueConProfesor(
            Profesor profesor, PeriodoAcademico periodo, Long idExcluido, HorarioGrupoRequestDTO bloque) {

        return validacionHorarioProfesorService.buscarConflicto(
                profesor.getId(),
                periodo.getId(),
                periodo.getFechaInicio(),
                periodo.getFechaFin(),
                idExcluido,
                List.of(new ValidacionHorarioProfesorService.BloqueHorario(
                        bloque.getDiaSemana(), bloque.getHoraInicio(), bloque.getHoraFin()))
        ).map(conflicto -> {
            Grupo otro = conflicto.grupo();
            HorarioGrupo h = conflicto.horario();
            String que = otro == null
                    ? "otro grupo"
                    : (otro.esEspacioInstrumento() ? "el espacio " : "el grupo ") + otro.getCodigo();
            return "Choca con " + que + " del " + FormatoHorario.bloque(h.getDiaSemana(), h.getHoraInicio(), h.getHoraFin())
                    + " de este mismo profesor. Cambie la hora o quite este espacio.";
        });
    }

    private Grupo obtenerEspacio(Long idGrupo) {
        Grupo grupo = grupoRepository.findById(idGrupo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El espacio indicado no existe."));

        if (!grupo.esEspacioInstrumento()) {
            throw new BusinessException("El grupo indicado no es un espacio de instrumento.");
        }
        return grupo;
    }

    /*
     * Código automático: ESP-<3 letras del instrumento>-<idProfesor>-<n>,
     * por ejemplo ESP-PIA-12-1.
     */
    static String prefijoCodigo(Instrumento instrumento, Profesor profesor) {
        String base = instrumento.getCodigo() != null && !instrumento.getCodigo().isBlank()
                ? instrumento.getCodigo()
                : instrumento.getNombre();
        String letras = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
        if (letras.length() > 3) {
            letras = letras.substring(0, 3);
        }
        if (letras.isEmpty()) {
            letras = "INS";
        }
        return "ESP-" + letras + "-" + profesor.getId() + "-";
    }

    /* =================================================================
     * Conversión a respuesta
     * ================================================================= */

    private List<EspacioInstrumentoResponse> mapear(List<Grupo> espacios) {

        if (espacios.isEmpty()) {
            return List.of();
        }

        List<Long> ids = espacios.stream().map(Grupo::getId).toList();

        Map<Long, HorarioGrupo> horarios = new HashMap<>();
        horarioGrupoRepository.findByIdGrupoInOrderByDiaSemanaAscHoraInicioAsc(ids)
                .forEach(h -> horarios.putIfAbsent(h.getIdGrupo(), h));

        Map<Long, PeriodoAcademico> periodos = porId(
                periodoRepository.findAllById(espacios.stream().map(Grupo::getIdPeriodo).distinct().toList()),
                PeriodoAcademico::getId);

        Map<Integer, Instrumento> instrumentos = new HashMap<>();
        instrumentoRepository.findAllById(espacios.stream().map(Grupo::getIdInstrumento)
                        .filter(Objects::nonNull).distinct().toList())
                .forEach(i -> instrumentos.put(i.getId(), i));

        Map<Long, Profesor> profesores = porId(
                profesorRepository.findAllById(espacios.stream().map(Grupo::getIdProfesor)
                        .filter(Objects::nonNull).distinct().toList()),
                Profesor::getId);

        // Ocupación: [idGrupo, idCurso (nivel), idEstudiante]
        Map<Long, Object[]> ocupantes = new HashMap<>();
        matriculaDetalleRepository.findOcupantesDeGrupos(ids, ESTADO_MATRICULA_REGISTRADA)
                .forEach(fila -> ocupantes.putIfAbsent(((Number) fila[0]).longValue(), fila));

        Map<Long, Estudiante> estudiantes = ocupantes.isEmpty() ? Map.of() : porId(
                estudianteRepository.findAllById(ocupantes.values().stream()
                        .map(f -> ((Number) f[2]).longValue()).distinct().toList()),
                Estudiante::getId);

        List<Long> idsNivel = ocupantes.values().stream()
                .map(f -> f[1]).filter(Objects::nonNull)
                .map(v -> ((Number) v).longValue()).distinct().toList();
        Map<Long, Curso> niveles = idsNivel.isEmpty() ? Map.of()
                : porId(cursoRepository.findAllById(idsNivel), Curso::getId);

        return espacios.stream()
                .map(g -> {
                    HorarioGrupo h = horarios.get(g.getId());
                    PeriodoAcademico periodo = periodos.get(g.getIdPeriodo());
                    Instrumento instrumento = g.getIdInstrumento() == null ? null : instrumentos.get(g.getIdInstrumento());
                    Profesor profesor = g.getIdProfesor() == null ? null : profesores.get(g.getIdProfesor());
                    Object[] ocupante = ocupantes.get(g.getId());

                    Estudiante estudiante = ocupante == null ? null
                            : estudiantes.get(((Number) ocupante[2]).longValue());
                    Long idNivel = ocupante == null || ocupante[1] == null ? null : ((Number) ocupante[1]).longValue();
                    Curso nivel = idNivel == null ? null : niveles.get(idNivel);

                    return new EspacioInstrumentoResponse(
                            g.getId(),
                            g.getCodigo(),
                            g.getIdPeriodo(),
                            periodo != null ? periodo.getNombre() : null,
                            g.getIdInstrumento(),
                            instrumento != null ? instrumento.getNombre() : null,
                            g.getIdProfesor(),
                            profesor != null ? nombreCompleto(profesor.getNombres(), profesor.getPrimerApellido(), profesor.getSegundoApellido()) : null,
                            h != null ? h.getDiaSemana() : null,
                            h != null ? h.getHoraInicio() : null,
                            h != null ? h.getHoraFin() : null,
                            h != null && h.getAula() != null ? h.getAula() : g.getAula(),
                            g.getEstado(),
                            ocupante != null,
                            estudiante != null ? estudiante.getId() : null,
                            estudiante != null ? nombreCompleto(estudiante.getNombres(), estudiante.getPrimerApellido(), estudiante.getSegundoApellido()) : null,
                            idNivel,
                            nivel != null ? nivel.getNombre() : null
                    );
                })
                .sorted(Comparator
                        .comparing(EspacioInstrumentoResponse::diaSemana, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(EspacioInstrumentoResponse::horaInicio, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
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

    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
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
}
