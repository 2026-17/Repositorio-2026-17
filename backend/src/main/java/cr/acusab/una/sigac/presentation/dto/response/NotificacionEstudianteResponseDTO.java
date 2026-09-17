package cr.acusab.una.sigac.presentation.dto.response;

import java.time.Instant;

public class NotificacionEstudianteResponseDTO {

    private Long idNotificacion;
    private Long idAutorizacion;

    private String tipo;
    private String asunto;
    private String mensaje;

    private Boolean leida;

    private Instant fechaNotificacion;
    private Instant fechaLectura;

    public Long getIdNotificacion() {
        return idNotificacion;
    }

    public void setIdNotificacion(Long idNotificacion) {
        this.idNotificacion = idNotificacion;
    }

    public Long getIdAutorizacion() {
        return idAutorizacion;
    }

    public void setIdAutorizacion(Long idAutorizacion) {
        this.idAutorizacion = idAutorizacion;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getAsunto() {
        return asunto;
    }

    public void setAsunto(String asunto) {
        this.asunto = asunto;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public Boolean getLeida() {
        return leida;
    }

    public void setLeida(Boolean leida) {
        this.leida = leida;
    }

    public Instant getFechaNotificacion() {
        return fechaNotificacion;
    }

    public void setFechaNotificacion(Instant fechaNotificacion) {
        this.fechaNotificacion = fechaNotificacion;
    }

    public Instant getFechaLectura() {
        return fechaLectura;
    }

    public void setFechaLectura(Instant fechaLectura) {
        this.fechaLectura = fechaLectura;
    }
}