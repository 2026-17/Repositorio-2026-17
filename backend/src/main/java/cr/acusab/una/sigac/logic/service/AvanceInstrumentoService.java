package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.CursoReconocido;
import cr.acusab.una.sigac.data.entity.MallaCurricular;
import cr.acusab.una.sigac.data.repository.CursoReconocidoRepository;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.MallaCurricularRepository;
import cr.acusab.una.sigac.data.repository.MatriculaDetalleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * Avance académico del estudiante, con los cursos de instrumento
 * evaluados POR INSTRUMENTO.
 *
 * Un estudiante puede llevar varios instrumentos y cada uno avanza por
 * separado: aprobar Instrumento V en piano no lo aprueba en guitarra. Los
 * demás cursos (Solfeo, Coro, Teoría...) se aprueban una sola vez.
 *
 * Fuentes del avance:
 * - Calificaciones APROBADO de SIGAC. En un curso de instrumento cuentan
 *   solo las de un espacio de ESE instrumento (el nivel es
 *   matriculas_detalles.id_curso).
 * - cursos_reconocidos: certificaciones anteriores a SIGAC y niveles
 *   convalidados por un nivel de ingreso.
 */
@Service
public class AvanceInstrumentoService {

    // Origen de un aprobado que viene de una calificación de SIGAC.
    public static final String ORIGEN_SIGAC = "SIGAC";

    /*
     * Un nivel de instrumento aprobado o convalidado.
     * condicion: APROBADO o CONVALIDADO.
     * origen: SIGAC, CERTIFICACION o NIVEL_INGRESO.
     * idPeriodo solo viene cuando el origen es SIGAC; fecha, cuando es
     * cursos_reconocidos.
     */
    public record NivelAprobado(
            Long idCurso,
            String condicion,
            String origen,
            Long idPeriodo,
            LocalDate fecha,
            BigDecimal nota
    ) {
    }

    /*
     * Curso que cuenta como aprobado para los requisitos.
     * Lo reconocido fuera de SIGAC no tiene nota: cumple siempre, igual
     * que en el trigger de requisitos de la base de datos.
     */
    public record CursoAprobado(BigDecimal nota, boolean reconocido) {

        public boolean cumple(BigDecimal notaMinima) {
            return reconocido
                    || notaMinima == null
                    || (nota != null && nota.compareTo(notaMinima) >= 0);
        }
    }

    private final MatriculaDetalleRepository matriculaDetalleRepository;
    private final CursoReconocidoRepository cursoReconocidoRepository;
    private final MallaCurricularRepository mallaRepository;
    private final CursoRepository cursoRepository;

    public AvanceInstrumentoService(
            MatriculaDetalleRepository matriculaDetalleRepository,
            CursoReconocidoRepository cursoReconocidoRepository,
            MallaCurricularRepository mallaRepository,
            CursoRepository cursoRepository
    ) {
        this.matriculaDetalleRepository = matriculaDetalleRepository;
        this.cursoReconocidoRepository = cursoReconocidoRepository;
        this.mallaRepository = mallaRepository;
        this.cursoRepository = cursoRepository;
    }

    /*
     * Niveles de instrumento aprobados o convalidados en un instrumento:
     * idCurso -> detalle. Si un nivel está en SIGAC y también reconocido,
     * gana SIGAC (tiene nota y período).
     */
    @Transactional(readOnly = true)
    public Map<Long, NivelAprobado> nivelesAprobados(Long idEstudiante, Integer idInstrumento) {

        Map<Long, NivelAprobado> resultado = new LinkedHashMap<>();

        for (Object[] fila : matriculaDetalleRepository.findNivelesInstrumentoAprobados(idEstudiante)) {
            Integer instrumentoFila = fila[1] == null ? null : ((Number) fila[1]).intValue();
            if (!Objects.equals(instrumentoFila, idInstrumento)) {
                continue;
            }
            Long idCurso = ((Number) fila[0]).longValue();
            resultado.put(idCurso, new NivelAprobado(
                    idCurso,
                    CursoReconocido.CONDICION_APROBADO,
                    ORIGEN_SIGAC,
                    fila[3] == null ? null : ((Number) fila[3]).longValue(),
                    null,
                    decimal(fila[2])
            ));
        }

        for (CursoReconocido reconocido :
                cursoReconocidoRepository.findByIdEstudianteAndIdInstrumento(idEstudiante, idInstrumento)) {
            resultado.putIfAbsent(reconocido.getIdCurso(), new NivelAprobado(
                    reconocido.getIdCurso(),
                    reconocido.getCondicion(),
                    reconocido.getOrigen(),
                    null,
                    reconocido.getFecha(),
                    null
            ));
        }

        return resultado;
    }

    /*
     * ¿Tiene niveles de ese instrumento aprobados con calificación de
     * SIGAC? (El nivel de ingreso no se puede registrar en ese caso.)
     */
    @Transactional(readOnly = true)
    public boolean tieneNivelesAprobadosEnSigac(Long idEstudiante, Integer idInstrumento) {
        return nivelesAprobados(idEstudiante, idInstrumento)
                .values()
                .stream()
                .anyMatch(nivel -> ORIGEN_SIGAC.equals(nivel.origen()));
    }

    /*
     * Cursos de instrumento de la malla del plan (Instrumento I..XIV),
     * en el orden de la malla: nivel y orden_en_nivel.
     */
    @Transactional(readOnly = true)
    public List<Curso> nivelesDelPlan(Long idPlanEstudio) {

        if (idPlanEstudio == null) {
            return List.of();
        }

        List<MallaCurricular> malla =
                mallaRepository.findByIdPlanEstudioAndActivoTrueOrderByNivelAscOrdenEnNivelAsc(idPlanEstudio);

        if (malla.isEmpty()) {
            return List.of();
        }

        Map<Long, Curso> cursos = cursoRepository
                .findAllById(malla.stream().map(MallaCurricular::getIdCurso).toList())
                .stream()
                .collect(Collectors.toMap(Curso::getId, Function.identity()));

        return malla.stream()
                .map(m -> cursos.get(m.getIdCurso()))
                .filter(Objects::nonNull)
                .filter(c -> Boolean.TRUE.equals(c.getEsCursoInstrumento()))
                .filter(c -> Boolean.TRUE.equals(c.getActivo()))
                .distinct()
                .toList();
    }

    /*
     * Siguiente nivel del estudiante en un instrumento: el primer curso de
     * instrumento de la malla que no esté aprobado ni convalidado en ESE
     * instrumento. Sin ninguno aprobado es Instrumento I. Vacío si ya
     * completó todos (o el plan no tiene cursos de instrumento).
     */
    @Transactional(readOnly = true)
    public Optional<Curso> siguienteNivel(Long idEstudiante, Integer idInstrumento, Long idPlanEstudio) {

        Map<Long, NivelAprobado> aprobados = nivelesAprobados(idEstudiante, idInstrumento);

        return nivelesDelPlan(idPlanEstudio)
                .stream()
                .filter(curso -> !aprobados.containsKey(curso.getId()))
                .findFirst();
    }

    /*
     * De los planes del estudiante, el primero cuya malla tiene cursos de
     * instrumento (de ahí salen los niveles I..XIV).
     */
    @Transactional(readOnly = true)
    public Optional<Long> planConInstrumento(Collection<Long> idsPlan) {
        return idsPlan.stream()
                .sorted()
                .filter(idPlan -> !nivelesDelPlan(idPlan).isEmpty())
                .findFirst();
    }

    /*
     * Cursos aprobados para los requisitos de un curso que NO es de
     * instrumento: los demás cursos (SIGAC + cursos_reconocidos) y los
     * niveles de instrumento aprobados en cualquier instrumento.
     */
    @Transactional(readOnly = true)
    public Map<Long, CursoAprobado> cursosAprobados(Long idEstudiante) {

        Map<Long, CursoAprobado> resultado = aprobadosGenerales(idEstudiante);

        for (Object[] fila : matriculaDetalleRepository.findNivelesInstrumentoAprobados(idEstudiante)) {
            agregarMejor(resultado, ((Number) fila[0]).longValue(), new CursoAprobado(decimal(fila[2]), false));
        }

        cursoReconocidoRepository.findByIdEstudiante(idEstudiante)
                .stream()
                .filter(r -> r.getIdInstrumento() != null)
                .forEach(r -> agregarMejor(resultado, r.getIdCurso(), new CursoAprobado(null, true)));

        return resultado;
    }

    /*
     * Cursos aprobados para los requisitos de un nivel de instrumento que
     * se cursa en un espacio de ese instrumento: los demás cursos, y de
     * los cursos de instrumento solo lo aprobado en ESE instrumento.
     */
    @Transactional(readOnly = true)
    public Map<Long, CursoAprobado> cursosAprobadosParaInstrumento(Long idEstudiante, Integer idInstrumento) {

        Map<Long, CursoAprobado> resultado = aprobadosGenerales(idEstudiante);

        nivelesAprobados(idEstudiante, idInstrumento).values().forEach(nivel ->
                agregarMejor(resultado, nivel.idCurso(),
                        new CursoAprobado(nivel.nota(), !ORIGEN_SIGAC.equals(nivel.origen()))));

        return resultado;
    }

    /*
     * Cursos que no son de instrumento: calificaciones de SIGAC y reconocidos
     * sin instrumento. Lo aprobado EN una agrupación no entra aquí: se cuenta
     * por agrupación (D5, AvanceAgrupacionService.cursosAprobadosDelPlan).
     */
    private Map<Long, CursoAprobado> aprobadosGenerales(Long idEstudiante) {

        Map<Long, CursoAprobado> resultado = new HashMap<>();

        for (Object[] fila : matriculaDetalleRepository.findCursosAprobados(idEstudiante)) {
            resultado.put(((Number) fila[0]).longValue(), new CursoAprobado(decimal(fila[1]), false));
        }

        cursoReconocidoRepository.findByIdEstudiante(idEstudiante)
                .stream()
                .filter(r -> r.getIdInstrumento() == null && r.getIdAgrupacion() == null)
                .forEach(r -> agregarMejor(resultado, r.getIdCurso(), new CursoAprobado(null, true)));

        return resultado;
    }

    // Si el curso ya está, se conserva el que más cumple (reconocido o con mejor nota).
    private static void agregarMejor(Map<Long, CursoAprobado> mapa, Long idCurso, CursoAprobado nuevo) {
        mapa.merge(idCurso, nuevo, (actual, otro) -> {
            if (actual.reconocido()) return actual;
            if (otro.reconocido()) return otro;
            if (actual.nota() == null) return otro;
            if (otro.nota() == null) return actual;
            return actual.nota().compareTo(otro.nota()) >= 0 ? actual : otro;
        });
    }

    private static BigDecimal decimal(Object valor) {
        return valor == null ? null : new BigDecimal(valor.toString());
    }
}
