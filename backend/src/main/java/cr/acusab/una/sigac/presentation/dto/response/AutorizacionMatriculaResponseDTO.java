package cr.acusab.una.sigac.presentation.dto.response;

import java.time.Instant;

public class AutorizacionMatriculaResponseDTO {

    private Long idAutorizacion;

    private Long idEstudiante;
    private String estudiante;

    private Long idPeriodo;
    private String codigoPeriodo;
    private String periodo;

    private String estado;

    private Boolean mensualidadesAlDia;
    private Boolean pagoMatriculaConfirmado;
    private Boolean documentacionCompleta;
    private Boolean estudianteActivo;
    private Boolean datosCompletos;
    private Boolean puedeMatricular;

    private Long idDocumentoComprobanteMatricula;
    private String comprobanteMatricula;

    private Instant fechaRevision;
    private String revisadoPor;
    private String observaciones;

    public Long getIdAutorizacion() {
        return idAutorizacion;
    }

    public void setIdAutorizacion(Long idAutorizacion) {
        this.idAutorizacion = idAutorizacion;
    }

    public Long getIdEstudiante() {
        return idEstudiante;
    }

    public void setIdEstudiante(Long idEstudiante) {
        this.idEstudiante = idEstudiante;
    }

    public String getEstudiante() {
        return estudiante;
    }

    public void setEstudiante(String estudiante) {
        this.estudiante = estudiante;
    }

    public Long getIdPeriodo() {
        return idPeriodo;
    }

    public void setIdPeriodo(Long idPeriodo) {
        this.idPeriodo = idPeriodo;
    }

    public String getCodigoPeriodo() {
        return codigoPeriodo;
    }

    public void setCodigoPeriodo(String codigoPeriodo) {
        this.codigoPeriodo = codigoPeriodo;
    }

    public String getPeriodo() {
        return periodo;
    }

    public void setPeriodo(String periodo) {
        this.periodo = periodo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Boolean getMensualidadesAlDia() {
        return mensualidadesAlDia;
    }

    public void setMensualidadesAlDia(Boolean mensualidadesAlDia) {
        this.mensualidadesAlDia = mensualidadesAlDia;
    }

    public Boolean getPagoMatriculaConfirmado() {
        return pagoMatriculaConfirmado;
    }

    public void setPagoMatriculaConfirmado(Boolean pagoMatriculaConfirmado) {
        this.pagoMatriculaConfirmado = pagoMatriculaConfirmado;
    }

    public Boolean getDocumentacionCompleta() {
        return documentacionCompleta;
    }

    public void setDocumentacionCompleta(Boolean documentacionCompleta) {
        this.documentacionCompleta = documentacionCompleta;
    }

    public Boolean getEstudianteActivo() {
        return estudianteActivo;
    }

    public void setEstudianteActivo(Boolean estudianteActivo) {
        this.estudianteActivo = estudianteActivo;
    }

    public Boolean getDatosCompletos() {
        return datosCompletos;
    }

    public void setDatosCompletos(Boolean datosCompletos) {
        this.datosCompletos = datosCompletos;
    }

    public Boolean getPuedeMatricular() {
        return puedeMatricular;
    }

    public void setPuedeMatricular(Boolean puedeMatricular) {
        this.puedeMatricular = puedeMatricular;
    }

    public Long getIdDocumentoComprobanteMatricula() {
        return idDocumentoComprobanteMatricula;
    }

    public void setIdDocumentoComprobanteMatricula(
            Long idDocumentoComprobanteMatricula
    ) {
        this.idDocumentoComprobanteMatricula =
                idDocumentoComprobanteMatricula;
    }

    public String getComprobanteMatricula() {
        return comprobanteMatricula;
    }

    public void setComprobanteMatricula(String comprobanteMatricula) {
        this.comprobanteMatricula = comprobanteMatricula;
    }

    public Instant getFechaRevision() {
        return fechaRevision;
    }

    public void setFechaRevision(Instant fechaRevision) {
        this.fechaRevision = fechaRevision;
    }

    public String getRevisadoPor() {
        return revisadoPor;
    }

    public void setRevisadoPor(String revisadoPor) {
        this.revisadoPor = revisadoPor;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}