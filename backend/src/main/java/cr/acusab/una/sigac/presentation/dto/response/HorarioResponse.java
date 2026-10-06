package cr.acusab.una.sigac.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HorarioResponse {
    private Long id;
    private Short diaSemana;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String aula;
}
