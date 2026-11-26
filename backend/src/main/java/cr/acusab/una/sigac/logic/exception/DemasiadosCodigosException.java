package cr.acusab.una.sigac.logic.exception;

import java.time.Instant;

/**
 * «Olvidé mi contraseña»: la cédula ya pidió demasiados códigos. Se responde
 * 429 DEMASIADOS_CODIGOS con restantes y proximoEn (GlobalExceptionHandler).
 */
public class DemasiadosCodigosException extends RuntimeException {

    public static final String CODIGO = "DEMASIADOS_CODIGOS";

    private final int restantes;
    private final Instant proximoEn;

    public DemasiadosCodigosException(String mensaje, int restantes, Instant proximoEn) {
        super(mensaje);
        this.restantes = restantes;
        this.proximoEn = proximoEn;
    }

    public int getRestantes() {
        return restantes;
    }

    public Instant getProximoEn() {
        return proximoEn;
    }
}
