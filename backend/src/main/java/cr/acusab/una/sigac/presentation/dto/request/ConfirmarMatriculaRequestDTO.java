package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ConfirmarMatriculaRequestDTO {

    @NotBlank(message = "La decisión es obligatoria.")
    @Pattern(
            regexp = "APROBAR|RECHAZAR",
            message = "La decisión debe ser APROBAR o RECHAZAR."
    )
    private String decision;

    @Size(
            max = 1500,
            message = "Las observaciones no pueden superar los 1500 caracteres."
    )
    private String observaciones;

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}