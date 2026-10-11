package cr.acusab.una.sigac.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GrupoResponseDTO {

    private Long id;
    private String codigo;

    private Long idCurso;
    private String nombreCurso;

    private Long idPeriodo;
    private String nombrePeriodo;

    private Long idProfesor;
    private String nombreProfesor;

    private Short cupoMaximo;
    private String modalidad;
    private String aula;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estado;

    private List<Horario> horarios;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Horario {
        private Long id;
        private Short diaSemana;
        private LocalTime horaInicio;
        private LocalTime horaFin;
        private String aula;
    }
}
