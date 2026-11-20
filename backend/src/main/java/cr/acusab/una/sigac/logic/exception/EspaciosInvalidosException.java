package cr.acusab.una.sigac.logic.exception;

import java.util.List;

/*
 * Espacios de instrumento - Uno o más bloques del lote no se pueden crear.
 * La creación es todo o nada: no se crea ninguno y se informa, por cada
 * bloque con problema, su posición en la lista (desde 0) y el motivo.
 */
public class EspaciosInvalidosException extends BusinessException {

    public record ErrorBloque(int indice, String mensaje) {
    }

    private final List<ErrorBloque> errores;

    public EspaciosInvalidosException(List<ErrorBloque> errores) {
        super(errores.size() == 1
                ? "El espacio " + (errores.get(0).indice() + 1) + " no se puede crear: " + errores.get(0).mensaje()
                : errores.size() + " espacios tienen errores. No se creó ninguno.");
        this.errores = List.copyOf(errores);
    }

    public List<ErrorBloque> getErrores() {
        return errores;
    }
}
