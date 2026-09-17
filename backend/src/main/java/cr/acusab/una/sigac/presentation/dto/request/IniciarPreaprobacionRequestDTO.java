package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotNull;

public class IniciarPreaprobacionRequestDTO {

    @NotNull(message = "El estudiante es obligatorio.")
    private Long idEstudiante;

    @NotNull(message = "El período académico es obligatorio.")
    private Long idPeriodo;

    public Long getIdEstudiante() {
        return idEstudiante;
    }

    public void setIdEstudiante(Long idEstudiante) {
        this.idEstudiante = idEstudiante;
    }

    public Long getIdPeriodo() {
        return idPeriodo;
    }

    public void setIdPeriodo(Long idPeriodo) {
        this.idPeriodo = idPeriodo;
    }
}
