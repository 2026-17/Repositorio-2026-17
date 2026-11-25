package cr.acusab.una.sigac.presentation.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/*
 * HU-10 - Consultar cursos disponibles de un período académico
 * (previo a solicitar matrícula).
 *
 * Cada fila representa un grupo ofertado: un mismo curso puede
 * aparecer varias veces si tiene más de un grupo en el período.
 */
@Getter
@Setter
public class CursoDisponibleResponseDTO {

    private Long idCurso;
    private String codigoCurso;
    private String nombreCurso;
    private Short nivel;

    private Long idGrupo;
    private String codigoGrupo;
    private String profesor;
    private String modalidad;
    private String aula;

    private int cupoMaximo;
    private int cupoDisponible;

    private List<HorarioDTO> horarios = new ArrayList<>();

    // Curso a coordinar en Dirección: sin horario ni profesor fijos.
    private boolean aCoordinar;

    @com.fasterxml.jackson.annotation.JsonProperty("aCoordinar")
    public boolean isACoordinar() {
        return aCoordinar;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("aCoordinar")
    public void setACoordinar(boolean aCoordinar) {
        this.aCoordinar = aCoordinar;
    }

    @Getter
    @Setter
    public static class HorarioDTO {
        private int diaSemana;
        private String horaInicio;
        private String horaFin;
        private String aula;
    }
}
