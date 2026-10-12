package cr.acusab.una.sigac.presentation.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/*
 * Horario semanal del estudiante autenticado: los grupos que tiene
 * matriculados (no retirados) en un período, con sus bloques de horario.
 *
 * periodos: todos los períodos en los que el estudiante tiene cursos
 * matriculados, para poder cambiar de uno a otro. periodo: el que se
 * muestra. Si el estudiante no tiene cursos, periodo es null y las
 * listas vienen vacías.
 */
@Getter
@Setter
public class HorarioEstudianteResponseDTO {

    private OfertaMatriculaResponseDTO.EstudianteDTO estudiante;
    private PeriodoDTO periodo;
    private List<PeriodoDTO> periodos = new ArrayList<>();
    private List<OfertaMatriculaResponseDTO.GrupoDTO> clases = new ArrayList<>();

    @Getter
    @Setter
    public static class PeriodoDTO {
        private Long id;
        private String codigo;
        private String nombre;
        private String estado;
        private LocalDate fechaInicio;
        private LocalDate fechaFin;
    }
}
