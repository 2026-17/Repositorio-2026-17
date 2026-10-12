package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.TiposIdentificacion;
import cr.acusab.una.sigac.logic.exception.BusinessException;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Formato del número de identificación según su tipo. Se reconoce el tipo
 * por su código (CEDULA, DIMEX, PASAPORTE) o, si el código es otro, por su
 * nombre. Un tipo que no corresponde a ninguno de los tres no tiene regla de
 * formato (solo el largo máximo del DTO). El frontend aplica las mismas
 * reglas en utils/validaciones.js.
 */
public final class ReglasIdentificacion {

    public enum Formato { CEDULA, DIMEX, PASAPORTE }

    private ReglasIdentificacion() {
    }

    public static Formato formatoDe(TiposIdentificacion tipo) {
        if (tipo == null) return null;
        String texto = sinTildes(tipo.getCodigo() + " " + tipo.getNombre());
        if (texto.contains("DIMEX")) return Formato.DIMEX;
        if (texto.contains("PASAPORTE")) return Formato.PASAPORTE;
        if (texto.contains("CEDULA") && !texto.contains("JURIDICA") && !texto.contains("RESIDENCIA")) {
            return Formato.CEDULA;
        }
        return null;
    }

    /**
     * Lanza BusinessException si el número no cumple el formato del tipo.
     * sujeto: a quién pertenece, para el mensaje (p. ej. "del encargado"); puede ser "".
     */
    public static void validar(TiposIdentificacion tipo, String numero, String sujeto) {
        Formato formato = formatoDe(tipo);
        if (formato == null || numero == null) return;
        String de = sujeto == null || sujeto.isBlank() ? "" : " " + sujeto.trim();
        switch (formato) {
            case CEDULA -> {
                if (!numero.matches("\\d{9}")) {
                    throw new BusinessException("La cédula nacional" + de + " debe tener 9 dígitos.");
                }
            }
            case DIMEX -> {
                if (!numero.matches("[A-Za-z0-9]{11,12}")) {
                    throw new BusinessException("El DIMEX" + de + " debe tener entre 11 y 12 letras o números.");
                }
            }
            case PASAPORTE -> {
                if (!numero.matches("[A-Za-z0-9]{6,20}")) {
                    throw new BusinessException("El pasaporte" + de + " debe tener entre 6 y 20 letras o números.");
                }
            }
        }
    }

    private static String sinTildes(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
    }
}
