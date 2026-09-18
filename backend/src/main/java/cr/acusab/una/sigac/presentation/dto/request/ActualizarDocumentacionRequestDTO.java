package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ActualizarDocumentacionRequestDTO {

    @NotNull(message = "Debe indicar si la documentación está completa.")
    private Boolean documentacionCompleta;

    @Size(
            max = 1500,
            message = "Las observaciones no pueden superar los 1500 caracteres."
    )
    private String observaciones;

    public Boolean getDocumentacionCompleta() {
        return documentacionCompleta;
    }

    public void setDocumentacionCompleta(Boolean documentacionCompleta) {
        this.documentacionCompleta = documentacionCompleta;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
