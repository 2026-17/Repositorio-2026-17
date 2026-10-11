package cr.acusab.una.sigac.presentation.dto.request;

/**
 * Reglas de composición de las contraseñas nuevas, compartidas por los DTO de
 * cambio y recuperación de contraseña. El frontend (RecuperarContrasena.jsx)
 * revisa las mismas reglas.
 */
public final class PoliticaContrasena {

    // Al menos una mayúscula, un número y un carácter que no sea letra ni número
    public static final String REGEX = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).*$";

    public static final String MENSAJE =
            "La contraseña debe incluir al menos una letra mayúscula, un número y un carácter especial";

    private PoliticaContrasena() {
    }
}
