package cr.acusab.una.sigac.logic.exception;

public class JwtInvalidoException extends RuntimeException {
    public JwtInvalidoException(String message) {
        super(message);
    }

    public JwtInvalidoException(String message, Throwable cause) {
        super(message, cause);
    }
}
