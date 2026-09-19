package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/*
 * HU-026 - Registrar instrumentos que aprende el estudiante.
 *
 * Se usa para asignar un nuevo instrumento al estudiante. Un
 * estudiante puede tener varios instrumentos vigentes a la vez;
 * no existe la nocion de instrumento "principal".
 */
public class InstrumentoEstudianteRequest {

    @NotNull(message = "El instrumento es obligatorio.")
    @Positive(message = "El instrumento seleccionado no es válido.")
    private Integer idInstrumento;

    @Size(max = 500, message = "Las observaciones no pueden superar los 500 caracteres.")
    private String observaciones;

    public Integer getIdInstrumento() {
        return idInstrumento;
    }

    public void setIdInstrumento(Integer idInstrumento) {
        this.idInstrumento = idInstrumento;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
