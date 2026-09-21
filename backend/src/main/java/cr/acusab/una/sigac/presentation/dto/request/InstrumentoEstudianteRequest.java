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

    @Positive(message = "El profesor seleccionado no es válido.")
    private Long idProfesor;

    /*
     * Indica si este instrumento pasa a ser el principal del
     * estudiante. Si ya existe un instrumento principal vigente,
     * se le registra fecha_fin y se conserva en el historial.
     *
     * Por defecto es true: la mayoría de asignaciones desde la
     * pantalla de expediente buscan fijar el instrumento principal.
     */
    private Boolean esPrincipal = true;
    @Size(max = 500, message = "Las observaciones no pueden superar los 500 caracteres.")
    private String observaciones;

    public Integer getIdInstrumento() {
        return idInstrumento;
    }

    public void setIdInstrumento(Integer idInstrumento) {
        this.idInstrumento = idInstrumento;
    }

    public Long getIdProfesor() {
        return idProfesor;
    }

    public void setIdProfesor(Long idProfesor) {
        this.idProfesor = idProfesor;
    }

    public Boolean getEsPrincipal() {
        return esPrincipal;
    }

    public void setEsPrincipal(Boolean esPrincipal) {
        this.esPrincipal = esPrincipal;
    }
    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
