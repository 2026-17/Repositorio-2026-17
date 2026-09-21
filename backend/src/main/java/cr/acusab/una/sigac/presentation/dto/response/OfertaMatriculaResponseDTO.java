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
