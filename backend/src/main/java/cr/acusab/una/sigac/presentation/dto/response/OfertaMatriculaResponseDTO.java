package cr.acusab.una.sigac.presentation.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Historia 2 - Todo lo que la pantalla de matrícula necesita del
 * estudiante autenticado: si está habilitado y, en ese caso, la oferta
 * de grupos, los requisitos y los cursos ya aprobados o matriculados.
 */
@Getter
@Setter
public class OfertaMatriculaResponseDTO {

    private EstudianteDTO estudiante;
    private PeriodoDTO periodo;

    private boolean habilitado;
    private String motivoNoHabilitado;

    private List<PlanDTO> planes = new ArrayList<>();
    private List<GrupoDTO> oferta = new ArrayList<>();
    private List<GrupoDTO> matriculados = new ArrayList<>();
    private Map<Long, List<RequisitoDTO>> requisitosPorCurso = new LinkedHashMap<>();
    private List<CursoAprobadoDTO> cursosAprobados = new ArrayList<>();

    /*
     * Lecciones de instrumento: una por instrumento vigente. Los cursos
     * Instrumento I..XIV no salen en "oferta": el estudiante elige uno de
     * los espacios libres de su profesor y el sistema le asigna el nivel.
     */
    private List<LeccionInstrumentoDTO> leccionesInstrumento = new ArrayList<>();

    @Getter
    @Setter
    public static class EstudianteDTO {
        private Long id;
        private String numeroIdentificacion;
        private String nombreCompleto;
        private boolean activo;
    }

    @Getter
    @Setter
    public static class PeriodoDTO {
        private Long id;
        private String codigo;
        private String nombre;
        private LocalDate fechaInicioMatricula;
        private LocalDate fechaFinMatricula;
    }

    @Getter
    @Setter
    public static class PlanDTO {
        private Long id;
        private String codigo;
        private String nombre;
    }

    @Getter
    @Setter
    public static class GrupoDTO {
        private Long idGrupo;
        private String codigo;
        private Long idCurso;
        private String codigoCurso;
        private String nombreCurso;
        private Long idPlanEstudio;
        private String nombrePlan;
        private Short nivel;
        private String profesor;
        private String modalidad;
        private String aula;
        private int cupoMaximo;
        private int cupoDisponible;
        private List<HorarioDTO> horarios = new ArrayList<>();

        /*
         * Espacio de instrumento (lección individual). idCurso y
         * nombreCurso son el nivel que el estudiante cursa en él
         * ("Instrumento VI · Piano").
         */
        private boolean espacioInstrumento;
        private Integer idInstrumento;
        private String nombreInstrumento;
        private String nombreNivel;

        /*
         * Agrupación (Orquesta, Coro...). Al estudiante se le muestra solo
         * el nombre: nombreCurso es el nombre de la agrupación y el nivel
         * NO viene (idCurso, codigoCurso, nivel y nombreNivel quedan null).
         * Tampoco viene si se le califica como coro (regla D4): el
         * estudiante no lo ve.
         */
        private boolean agrupacion;
        private Long idAgrupacion;
        private String nombreAgrupacion;
        private String tipoAgrupacion;
        private List<String> profesores = new ArrayList<>();

        /*
         * Oferta de cursos: por qué el estudiante no puede elegir el grupo
         * aunque tenga cupo (por ejemplo, la edad del curso, regla D7).
         */
        private String motivoNoDisponible;
    }

    /*
     * Lección de un instrumento vigente del estudiante.
     *
     * - nivel: el siguiente Instrumento de la malla en ESE instrumento.
     *   motivoNivel explica por qué ("Aprobó Instrumento V en ...").
     * - requerida: hay un nivel pendiente, así que la matrícula exige
     *   elegir un espacio de este instrumento.
     * - disponible = false con motivoNoDisponible: no puede elegirse
     *   (sin profesor asignado, requisitos incumplidos o sin espacios
     *   libres).
     * - espaciosLibres: espacios libres de su profesor e instrumento.
     * - matriculado: espacio que ya tiene en este período, si lo hay.
     */
    @Getter
    @Setter
    public static class LeccionInstrumentoDTO {
        private Long idEstudianteInstrumento;
        private Integer idInstrumento;
        private String nombreInstrumento;
        private boolean delPlan;
        private Long idProfesor;
        private String profesor;
        private Long idCursoNivel;
        private String codigoNivel;
        private String nombreNivel;
        private String motivoNivel;
        private boolean completado;
        private boolean requerida;
        private boolean disponible;
        private String motivoNoDisponible;
        private List<GrupoDTO> espaciosLibres = new ArrayList<>();
        private GrupoDTO matriculado;
    }

    @Getter
    @Setter
    public static class HorarioDTO {
        private int diaSemana;
        private String horaInicio;
        private String horaFin;
        private String aula;
    }

    /*
     * Curso que el estudiante ya aprobó. La nota puede ser nula en cursos
     * con valoración cualitativa.
     */
    @Getter
    @Setter
    public static class CursoAprobadoDTO {
        private Long idCurso;
        private BigDecimal nota;
    }

    /*
     * tipo: REQUISITO (prerrequisito, debe estar aprobado) o
     * CORREQUISITO (aprobado, ya matriculado o seleccionado a la vez).
     */
    @Getter
    @Setter
    public static class RequisitoDTO {
        private String tipo;
        private Long idCursoRequerido;
        private String codigoCursoRequerido;
        private String nombreCursoRequerido;
        private BigDecimal notaMinima;
    }
}
