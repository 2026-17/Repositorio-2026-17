package cr.acusab.una.sigac.presentation.dto.request;

/**
 * Reglas de formato de los datos de personas (estudiantes, encargados,
 * profesores y usuarios), compartidas por los DTO. El frontend revisa las
 * mismas reglas en utils/validaciones.js.
 */
public final class PoliticaDatosPersona {

    // Partes locales y dominios sin puntos al inicio, al final ni seguidos;
    // el dominio debe terminar en al menos dos letras.
    public static final String CORREO_REGEX =
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
                    + "@([A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?\\.)+[A-Za-z]{2,}$";
    // Para correos opcionales: acepta además el texto vacío.
    public static final String CORREO_OPCIONAL_REGEX = "^$|" + CORREO_REGEX;
    public static final String CORREO_MENSAJE = "El correo electrónico no tiene un formato válido.";

    // Exactamente 8 dígitos. La versión opcional acepta además el texto vacío.
    public static final String TELEFONO_REGEX = "^\\d{8}$";
    public static final String TELEFONO_OPCIONAL_REGEX = "^(\\d{8})?$";
    public static final String TELEFONO_MENSAJE = "El teléfono debe tener 8 dígitos (ej. 8888-4321).";

    // Letras (incluye tildes y ñ), espacios, apóstrofo y guion; empieza con letra.
    // La versión opcional acepta además el texto vacío.
    public static final String NOMBRE_REGEX = "^\\p{L}[\\p{L}\\p{M}' -]*$";
    public static final String NOMBRE_OPCIONAL_REGEX = "^(\\p{L}[\\p{L}\\p{M}' -]*)?$";
    public static final String NOMBRE_MENSAJE = "Solo se permiten letras.";

    // Identificación de una cuenta de usuario: no tiene tipo asociado, así
    // que solo se exige lo común a todos los tipos (letras y números).
    public static final String IDENTIFICACION_USUARIO_REGEX = "^[A-Za-z0-9]+$";
    public static final String IDENTIFICACION_USUARIO_MENSAJE =
            "La identificación solo puede tener letras y números.";

    private PoliticaDatosPersona() {
    }
}
