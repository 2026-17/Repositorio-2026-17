package cr.acusab.una.sigac.presentation.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/*
 * Matrícula - Agrupaciones que el estudiante puede elegir en el período
 * (mockup 8 · Estudiante: elegir agrupaciones).
 *
 * EL NIVEL ES INTERNO: aquí nunca viene el nivel ni su motivo, solo el
 * nombre de la agrupación. Tampoco viene nada de la regla del coro (D4):
 * es automática y el estudiante no la ve.
 */
@Getter
@Setter
public class AgrupacionesDisponiblesResponseDTO {

    private boolean habilitado;
    private String motivoNoHabilitado;
    private OfertaMatriculaResponseDTO.PeriodoDTO periodo;
    private List<AgrupacionDTO> agrupaciones = new ArrayList<>();

    /*
     * bloqueada + motivoBloqueo: "Es solo para estudiantes de Violín o
     * Viola", "No tiene cupos libres" o "Ya completó esta agrupación".
     * matriculada: ya la tiene en este período.
     */
    @Getter
    @Setter
    public static class AgrupacionDTO {
        private Long idGrupo;
        private String codigo;
        private Long idAgrupacion;
        private String nombre;
        private String tipo;
        private String descripcion;
        private String aula;
        private List<OfertaMatriculaResponseDTO.HorarioDTO> horarios = new ArrayList<>();
        private List<String> profesores = new ArrayList<>();
        private int cupoMaximo;
        private int cupoDisponible;
        private boolean bloqueada;
        private String motivoBloqueo;
        private boolean matriculada;
    }
}
