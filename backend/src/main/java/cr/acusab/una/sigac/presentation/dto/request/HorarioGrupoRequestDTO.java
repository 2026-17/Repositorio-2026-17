package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/*
 * Un bloque del horario semanal de un grupo.
 * diaSemana: 1 = lunes ... 7 = domingo.
 */
public class HorarioGrupoRequestDTO {

    @NotNull(message = "El día del horario es obligatorio.")
    @Min(value = 1, message = "El día del horario no es válido.")
    @Max(value = 7, message = "El día del horario no es válido.")
    private Short diaSemana;

    @NotNull(message = "La hora de inicio del horario es obligatoria.")
    private LocalTime horaInicio;

    @NotNull(message = "La hora de fin del horario es obligatoria.")
    private LocalTime horaFin;

    // Si se omite, se usa el aula del grupo.
    @Size(max = 80, message = "El aula del horario no puede superar los 80 caracteres.")
    private String aula;

    public Short getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(Short diaSemana) {
        this.diaSemana = diaSemana;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }

    public String getAula() {
        return aula;
    }

    public void setAula(String aula) {
        this.aula = aula;
    }
}
