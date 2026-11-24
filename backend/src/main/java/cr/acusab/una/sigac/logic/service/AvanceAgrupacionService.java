package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Agrupacion;
import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.CursoReconocido;
import cr.acusab.una.sigac.data.entity.PeriodoAcademico;
import cr.acusab.una.sigac.data.repository.CursoReconocidoRepository;
import cr.acusab.una.sigac.data.repository.CursoRepository;
import cr.acusab.una.sigac.data.repository.MatriculaDetalleRepository;
import cr.acusab.una.sigac.data.repository.PeriodoAcademicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/*
 * Avance de un estudiante POR AGRUPACIÓN.
 *
 * Un estudiante puede ir en Orquesta V y en Comercial VI en el mismo
 * período: cada agrupación avanza por separado. Las reglas D1 a D3 están
 * explicadas en ReglasAgrupaciones; los niveles de cada familia, en
 * FamiliaAgrupacion.
 *
 * Fuentes del avance (las mismas que los instrumentos):
 * - Calificaciones de SIGAC en grupos de la agrupación: el nivel es
 *   matriculas_detalles.id_curso y "aprobado" es el estado APROBADO.
 * - cursos_reconocidos con esa id_agrupacion.
 *
 * El nivel es INTERNO: el motivo en texto ("Aprobó Orquesta IV en ...")
 * solo se muestra a dirección académica, administración y profesores.
 */
@Service
public class AvanceAgrupacionService {

    // Un nivel de la familia: número (1 = I) y su curso.
    public record Nivel(int numero, Curso curso) {
    }

    /*
     * Siguiente nivel en una agrupación.
     * - curso != null: el nivel que le corresponde.
     * - completado: ya aprobó el nivel máximo (D3).
     * - curso == null y !completado: la familia no tiene cursos configurados.
     * motivo: SOLO para las vistas internas.
     */
    public record SiguienteNivel(Curso curso, int numero, boolean completado, String motivo) {

        public boolean disponible() {
            return curso != null;
        }
    }

    // Calificación de un grupo de agrupación.
    record Calificacion(Long idCurso, Long idAgrupacion, String estado, BigDecimal nota, Long idPeriodo) {

        boolean aprobada() {
            return ReglasAgrupaciones.ESTADO_ACADEMICO_APROBADO.equals(estado);
        }

        // Tiene resultado final y no es aprobado (no cuenta "en curso").
        boolean reprobada() {
            return estado != null && !aprobada()
                    && !ReglasAgrupaciones.ESTADOS_ACADEMICOS_SIN_RESULTADO.contains(estado);
        }
    }

    /*
     * Todo el historial del estudiante en agrupaciones, cargado una vez para
     * calcular varias agrupaciones sin repetir consultas.
     */
    public static final class Historial {

        private final Long idEstudiante;
        private final List<Calificacion> calificaciones;
        private final List<CursoReconocido> reconocidos;
        private final Map<FamiliaAgrupacion, TreeMap<Integer, Curso>> niveles;
        private final Map<Long, FamiliaAgrupacion> familiaPorCurso;

        Historial(Long idEstudiante, List<Calificacion> calificaciones, List<CursoReconocido> reconocidos,
                  Map<FamiliaAgrupacion, TreeMap<Integer, Curso>> niveles) {
            this.idEstudiante = idEstudiante;
            this.calificaciones = calificaciones;
            this.reconocidos = reconocidos;
            this.niveles = niveles;
            this.familiaPorCurso = new HashMap<>();
            niveles.forEach((familia, mapa) -> mapa.values().forEach(c -> familiaPorCurso.put(c.getId(), familia)));
        }

        public Long idEstudiante() {
            return idEstudiante;
        }

        int numero(Long idCurso, FamiliaAgrupacion familia) {
            if (idCurso == null || familiaPorCurso.get(idCurso) != familia) {
                return 0;
            }
            return niveles.get(familia).entrySet().stream()
                    .filter(e -> e.getValue().getId().equals(idCurso))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(0);
        }

        public FamiliaAgrupacion familiaDe(Long idCurso) {
            return idCurso == null ? null : familiaPorCurso.get(idCurso);
        }

        public List<Nivel> niveles(FamiliaAgrupacion familia) {
            return niveles.getOrDefault(familia, new TreeMap<>()).entrySet().stream()
                    .map(e -> new Nivel(e.getKey(), e.getValue()))
                    .toList();
        }
    }

    private final MatriculaDetalleRepository matriculaDetalleRepository;
    private final CursoReconocidoRepository cursoReconocidoRepository;
    private final CursoRepository cursoRepository;
    private final PeriodoAcademicoRepository periodoRepository;

    public AvanceAgrupacionService(
            MatriculaDetalleRepository matriculaDetalleRepository,
            CursoReconocidoRepository cursoReconocidoRepository,
            CursoRepository cursoRepository,
            PeriodoAcademicoRepository periodoRepository
    ) {
        this.matriculaDetalleRepository = matriculaDetalleRepository;
        this.cursoReconocidoRepository = cursoReconocidoRepository;
        this.cursoRepository = cursoRepository;
        this.periodoRepository = periodoRepository;
    }

    /* =================================================================
     * API por estudiante (cada llamada carga el historial)
     * ================================================================= */

    @Transactional(readOnly = true)
    public Map<Long, AvanceInstrumentoService.NivelAprobado> nivelesAprobados(
            Long idEstudiante, Long idAgrupacion, FamiliaAgrupacion familia) {
        return nivelesAprobados(cargarHistorial(idEstudiante), idAgrupacion, familia);
    }

    @Transactional(readOnly = true)
    public SiguienteNivel siguienteNivel(Long idEstudiante, Agrupacion agrupacion, FamiliaAgrupacion familia) {
        return siguienteNivel(cargarHistorial(idEstudiante), agrupacion, familia);
    }

    /* =================================================================
     * Historial precargado
     * ================================================================= */

    @Transactional(readOnly = true)
    public Historial cargarHistorial(Long idEstudiante) {

        List<Calificacion> calificaciones = new ArrayList<>();
        for (Object[] fila : matriculaDetalleRepository.findCalificacionesAgrupacion(idEstudiante)) {
            calificaciones.add(new Calificacion(
                    numeroLargo(fila[0]),
                    numeroLargo(fila[1]),
                    fila[2] == null ? null : fila[2].toString(),
                    fila[3] == null ? null : new BigDecimal(fila[3].toString()),
                    numeroLargo(fila[4])
            ));
        }

        return new Historial(
                idEstudiante,
                calificaciones,
                cursoReconocidoRepository.findByIdEstudiante(idEstudiante),
                nivelesConfigurados()
        );
    }

    // Cursos de cada familia que existen en la base, por número de nivel.
    private Map<FamiliaAgrupacion, TreeMap<Integer, Curso>> nivelesConfigurados() {

        List<String> codigos = Arrays.stream(FamiliaAgrupacion.values())
                .flatMap(f -> f.codigos().stream())
                .toList();

        Map<FamiliaAgrupacion, TreeMap<Integer, Curso>> niveles = new EnumMap<>(FamiliaAgrupacion.class);

        for (Curso curso : cursoRepository.findByCodigoIn(codigos)) {
            FamiliaAgrupacion.deCurso(curso).ifPresent(familia ->
                    niveles.computeIfAbsent(familia, f -> new TreeMap<>())
                            .put(familia.numero(curso.getCodigo()), curso));
        }

        return niveles;
    }

    /*
     * Niveles aprobados en una agrupación y familia: idCurso -> detalle.
     * Si un nivel está en SIGAC y también reconocido, gana SIGAC (tiene
     * nota y período), igual que en los instrumentos.
     */
    public Map<Long, AvanceInstrumentoService.NivelAprobado> nivelesAprobados(
            Historial historial, Long idAgrupacion, FamiliaAgrupacion familia) {

        Map<Long, AvanceInstrumentoService.NivelAprobado> resultado = new LinkedHashMap<>();

        for (Calificacion c : historial.calificaciones) {
            if (!c.aprobada() || !Objects.equals(c.idAgrupacion(), idAgrupacion)
                    || historial.numero(c.idCurso(), familia) == 0) {
                continue;
            }
            resultado.merge(c.idCurso(), new AvanceInstrumentoService.NivelAprobado(
                    c.idCurso(), CursoReconocido.CONDICION_APROBADO, AvanceInstrumentoService.ORIGEN_SIGAC,
                    c.idPeriodo(), null, c.nota()
            ), AvanceAgrupacionService::masReciente);
        }

        for (CursoReconocido r : historial.reconocidos) {
            if (!Objects.equals(r.getIdAgrupacion(), idAgrupacion) || idAgrupacion == null
                    || historial.numero(r.getIdCurso(), familia) == 0) {
                continue;
            }
            resultado.putIfAbsent(r.getIdCurso(), new AvanceInstrumentoService.NivelAprobado(
                    r.getIdCurso(), r.getCondicion(), r.getOrigen(), null, r.getFecha(), null));
        }

        return resultado;
    }

    // Niveles de la familia reconocidos SIN agrupación (carga inicial; regla D2).
    public Map<Long, AvanceInstrumentoService.NivelAprobado> nivelesSinAgrupacion(
            Historial historial, FamiliaAgrupacion familia) {

        Map<Long, AvanceInstrumentoService.NivelAprobado> resultado = new LinkedHashMap<>();

        for (CursoReconocido r : historial.reconocidos) {
            if (r.getIdAgrupacion() == null && historial.numero(r.getIdCurso(), familia) > 0) {
                resultado.putIfAbsent(r.getIdCurso(), new AvanceInstrumentoService.NivelAprobado(
                        r.getIdCurso(), r.getCondicion(), r.getOrigen(), null, r.getFecha(), null));
            }
        }

        return resultado;
    }

    /*
     * Siguiente nivel en una agrupación (reglas D1, D2 y D3), con el motivo
     * para las vistas internas.
     */
    public SiguienteNivel siguienteNivel(Historial historial, Agrupacion agrupacion, FamiliaAgrupacion familia) {

        TreeMap<Integer, Curso> niveles = historial.niveles.getOrDefault(familia, new TreeMap<>());

        if (niveles.isEmpty()) {
            return new SiguienteNivel(null, 0, false,
                    "No están configurados los cursos de " + familia.nombre() + " (" + familia.codigos().get(0)
                            + " a " + familia.codigos().get(familia.nivelMaximo() - 1) + ").");
        }

        Map<Long, AvanceInstrumentoService.NivelAprobado> aprobados =
                nivelesAprobados(historial, agrupacion.getId(), familia);

        // D1: el último aprobado en ESTA agrupación.
        int ultimoPropio = aprobados.keySet().stream()
                .mapToInt(id -> historial.numero(id, familia))
                .max()
                .orElse(0);

        String nombre = agrupacion.getNombre();

        if (ultimoPropio > 0) {

            if (ultimoPropio >= familia.nivelMaximo()) {
                return completado(nombre, familia, niveles);
            }

            int siguiente = ultimoPropio + 1;
            Long idAnterior = idCurso(niveles, ultimoPropio);
            String motivo = reproboNivel(historial, agrupacion.getId(), niveles, siguiente)
                    ? "No aprobó " + etiquetaNivel(nombre, siguiente) + ": la repite."
                    : motivoAprobado(etiquetaNivel(nombre, ultimoPropio), aprobados.get(idAnterior));

            return nivel(niveles, siguiente, motivo, familia);
        }

        // D2: punto de partida por el historial sin agrupación.
        int inicio = 1;
        String motivo = "Primera vez en " + nombre + ".";

        if (ReglasAgrupaciones.PARTIR_DEL_HISTORIAL_SIN_AGRUPACION) {

            int ultimoSinAgrupacion = historial.reconocidos.stream()
                    .filter(r -> r.getIdAgrupacion() == null)
                    .mapToInt(r -> historial.numero(r.getIdCurso(), familia))
                    .max()
                    .orElse(0);

            if (ultimoSinAgrupacion >= familia.nivelMaximo()) {
                return completado(nombre, familia, niveles);
            }

            if (ultimoSinAgrupacion > 0) {
                inicio = ultimoSinAgrupacion + 1;
                motivo = "Empieza en " + familia.abreviatura() + " " + FamiliaAgrupacion.romano(inicio)
                        + " por su historial de la certificación.";
            }
        }

        if (reproboNivel(historial, agrupacion.getId(), niveles, inicio)) {
            motivo = "No aprobó " + etiquetaNivel(nombre, inicio) + ": la repite.";
        }

        return nivel(niveles, inicio, motivo, familia);
    }

    /* =================================================================
     * Textos
     * ================================================================= */

    // "Orquesta" + 4 -> "Orquesta IV"
    public static String etiquetaNivel(String nombreAgrupacion, int numero) {
        return nombreAgrupacion + " " + FamiliaAgrupacion.romano(numero);
    }

    // Vistas internas: "Orquesta · Conjunto Instrumental V"
    public static String etiquetaInterna(String nombreAgrupacion, Curso nivel) {
        return nivel == null ? nombreAgrupacion : nombreAgrupacion + " · " + nivel.getNombre();
    }

    // D4: "Comercial · Coro III (se califica como coro)"
    public static String etiquetaInterna(String nombreAgrupacion, Curso nivel, boolean comoCoro) {
        String etiqueta = etiquetaInterna(nombreAgrupacion, nivel);
        return comoCoro ? etiqueta + " (se califica como coro)" : etiqueta;
    }

    /* =================================================================
     * D5: plan de estudios y graduación
     * ================================================================= */

    /*
     * Avance de una familia para el plan: el de la agrupación más adelantada.
     * - Cuentan los niveles aprobados en ESA agrupación más el historial sin
     *   agrupación (D2), que se toma como parte de ella.
     * - Aprobar el mismo nivel en dos agrupaciones no cuenta doble: cada
     *   agrupación se mide por separado.
     * - completa: tiene TODOS los niveles (I-XII o I-XIV) en una misma
     *   agrupación.
     * idAgrupacion = null si el avance solo viene del historial sin agrupación.
     */
    public record AvanceFamilia(
            FamiliaAgrupacion familia,
            Long idAgrupacion,
            Set<Long> cursosAprobados,
            int ultimoNivel,
            boolean completa
    ) {
    }

    public AvanceFamilia avanceFamilia(Historial historial, FamiliaAgrupacion familia) {

        TreeMap<Integer, Curso> niveles = historial.niveles.getOrDefault(familia, new TreeMap<>());

        Set<Long> sinAgrupacion = new HashSet<>(nivelesSinAgrupacion(historial, familia).keySet());

        // Agrupaciones con historial en esta familia.
        Set<Long> agrupaciones = new LinkedHashSet<>();
        historial.calificaciones.stream()
                .filter(c -> c.aprobada() && c.idAgrupacion() != null && historial.numero(c.idCurso(), familia) > 0)
                .forEach(c -> agrupaciones.add(c.idAgrupacion()));
        historial.reconocidos.stream()
                .filter(r -> r.getIdAgrupacion() != null && historial.numero(r.getIdCurso(), familia) > 0)
                .forEach(r -> agrupaciones.add(r.getIdAgrupacion()));

        Long mejorAgrupacion = null;
        Set<Long> mejor = sinAgrupacion;

        for (Long idAgrupacion : agrupaciones) {
            Set<Long> cursos = new HashSet<>(sinAgrupacion);
            cursos.addAll(nivelesAprobados(historial, idAgrupacion, familia).keySet());
            if (esMasAdelantado(historial, familia, cursos, mejor)) {
                mejor = cursos;
                mejorAgrupacion = idAgrupacion;
            }
        }

        Set<Long> elegido = mejor;
        int ultimo = ultimoNivel(historial, familia, elegido);
        boolean completa = niveles.size() == familia.nivelMaximo()
                && niveles.values().stream().allMatch(c -> elegido.contains(c.getId()));

        return new AvanceFamilia(familia, mejorAgrupacion, Set.copyOf(elegido), ultimo, completa);
    }

    // ¿Tiene todos los niveles de la familia en una misma agrupación?
    @Transactional(readOnly = true)
    public boolean familiaCompleta(Long idEstudiante, FamiliaAgrupacion familia) {
        return avanceFamilia(cargarHistorial(idEstudiante), familia).completa();
    }

    /*
     * Cursos de las familias que cuentan como aprobados para el plan
     * (requisitos y graduación): los de la agrupación más adelantada de cada
     * familia. Lo reconocido no tiene nota y cumple siempre, igual que en
     * AvanceInstrumentoService.
     */
    @Transactional(readOnly = true)
    public Map<Long, AvanceInstrumentoService.CursoAprobado> cursosAprobadosDelPlan(Long idEstudiante) {

        Historial historial = cargarHistorial(idEstudiante);
        Map<Long, AvanceInstrumentoService.CursoAprobado> resultado = new HashMap<>();

        for (FamiliaAgrupacion familia : FamiliaAgrupacion.values()) {
            AvanceFamilia avance = avanceFamilia(historial, familia);
            Map<Long, AvanceInstrumentoService.NivelAprobado> detalle = avance.idAgrupacion() == null
                    ? Map.of() : nivelesAprobados(historial, avance.idAgrupacion(), familia);
            for (Long idCurso : avance.cursosAprobados()) {
                AvanceInstrumentoService.NivelAprobado nivel = detalle.get(idCurso);
                boolean reconocido = nivel == null || !AvanceInstrumentoService.ORIGEN_SIGAC.equals(nivel.origen());
                resultado.put(idCurso, new AvanceInstrumentoService.CursoAprobado(
                        nivel != null ? nivel.nota() : null, reconocido));
            }
        }

        return resultado;
    }

    private static int ultimoNivel(Historial historial, FamiliaAgrupacion familia, Set<Long> cursos) {
        return cursos.stream().mapToInt(id -> historial.numero(id, familia)).max().orElse(0);
    }

    // Más niveles distintos aprobados; si empatan, el que llegó más alto.
    private static boolean esMasAdelantado(Historial historial, FamiliaAgrupacion familia, Set<Long> a, Set<Long> b) {
        if (a.size() != b.size()) {
            return a.size() > b.size();
        }
        return ultimoNivel(historial, familia, a) > ultimoNivel(historial, familia, b);
    }

    private String motivoAprobado(String etiqueta, AvanceInstrumentoService.NivelAprobado aprobado) {

        if (aprobado != null && AvanceInstrumentoService.ORIGEN_SIGAC.equals(aprobado.origen())) {
            String periodo = aprobado.idPeriodo() == null ? null
                    : periodoRepository.findById(aprobado.idPeriodo()).map(PeriodoAcademico::getNombre).orElse(null);
            return "Aprobó " + etiqueta + (periodo != null ? " en " + periodo : "") + ".";
        }

        return "Tiene aprobado " + etiqueta + " por certificación anterior a SIGAC.";
    }

    /* =================================================================
     * Apoyo
     * ================================================================= */

    private static SiguienteNivel completado(String nombre, FamiliaAgrupacion familia, TreeMap<Integer, Curso> niveles) {
        Curso ultimo = niveles.get(familia.nivelMaximo());
        return new SiguienteNivel(null, familia.nivelMaximo(), true,
                "Ya completó " + nombre + ": aprobó "
                        + (ultimo != null ? ultimo.getNombre() : familia.nombre() + " " + FamiliaAgrupacion.romano(familia.nivelMaximo()))
                        + ".");
    }

    private static SiguienteNivel nivel(TreeMap<Integer, Curso> niveles, int numero, String motivo, FamiliaAgrupacion familia) {
        Curso curso = niveles.get(numero);
        if (curso == null) {
            return new SiguienteNivel(null, numero, false,
                    "Falta el curso " + familia.codigos().get(numero - 1) + " en el catálogo de cursos.");
        }
        return new SiguienteNivel(curso, numero, false, motivo);
    }

    private static Long idCurso(TreeMap<Integer, Curso> niveles, int numero) {
        Curso curso = niveles.get(numero);
        return curso == null ? null : curso.getId();
    }

    // ¿Tiene una calificación NO aprobada de ese nivel en la agrupación?
    private static boolean reproboNivel(Historial historial, Long idAgrupacion, TreeMap<Integer, Curso> niveles, int numero) {
        Long idCurso = idCurso(niveles, numero);
        return idCurso != null && historial.calificaciones.stream()
                .anyMatch(c -> c.reprobada()
                        && Objects.equals(c.idAgrupacion(), idAgrupacion)
                        && idCurso.equals(c.idCurso()));
    }

    // Del mismo nivel aprobado dos veces se conserva el período más reciente.
    private static AvanceInstrumentoService.NivelAprobado masReciente(
            AvanceInstrumentoService.NivelAprobado a, AvanceInstrumentoService.NivelAprobado b) {
        if (a.idPeriodo() == null) return b;
        if (b.idPeriodo() == null) return a;
        return a.idPeriodo() >= b.idPeriodo() ? a : b;
    }

    private static Long numeroLargo(Object valor) {
        return valor == null ? null : ((Number) valor).longValue();
    }
}
