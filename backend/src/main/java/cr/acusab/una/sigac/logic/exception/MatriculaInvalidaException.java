package cr.acusab.una.sigac.logic.exception;

import java.util.List;

/*
 * Historia 2 - La selección de grupos incumple una o más reglas de
 * matrícula. Conserva todos los motivos para mostrarlos juntos.
 */
public class MatriculaInvalidaException extends BusinessException {

    private final List<String> errores;

    public MatriculaInvalidaException(List<String> errores) {
        super(errores.isEmpty()
                ? "La selección de cursos no es válida."
                : errores.get(0));
        this.errores = List.copyOf(errores);
    }

    public List<String> getErrores() {
        return errores;
    }
}
