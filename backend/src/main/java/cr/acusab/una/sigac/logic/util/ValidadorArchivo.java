package cr.acusab.una.sigac.logic.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/*
 * Validación de archivos subidos que pueden ser PDF o imagen (comprobantes
 * de pago y póliza del estudiante).
 *
 * El tipo MIME que declara el navegador se puede falsear: por eso, además
 * de revisar que sea uno de los permitidos, se comparan los primeros bytes
 * del archivo con la firma de cada formato.
 *
 * El archivo se guarda con un nombre aleatorio (UUID) y la extensión que
 * corresponde a su tipo real, nunca con el nombre que trae el usuario.
 */
public final class ValidadorArchivo {

    public static final String PDF = "application/pdf";
    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String WEBP = "image/webp";

    // PDF, JPG, PNG y WEBP.
    public static final Set<String> TIPOS_PDF_O_IMAGEN = Set.of(PDF, JPEG, PNG, WEBP);

    // Bytes suficientes para reconocer todas las firmas (WEBP llega al byte 12).
    private static final int BYTES_FIRMA = 12;

    private ValidadorArchivo() {
    }

    // Tipo MIME declarado, en minúsculas ("" si no viene).
    public static String tipoMime(MultipartFile archivo) {
        return Objects.toString(archivo.getContentType(), "").toLowerCase(Locale.ROOT).trim();
    }

    public static boolean esPdfOImagen(String tipoMime) {
        return TIPOS_PDF_O_IMAGEN.contains(tipoMime);
    }

    public static boolean esImagen(String tipoMime) {
        return JPEG.equals(tipoMime) || PNG.equals(tipoMime) || WEBP.equals(tipoMime);
    }

    // Primeros bytes del archivo, para comparar con la firma de su tipo.
    public static byte[] leerInicio(MultipartFile archivo) throws IOException {
        try (InputStream input = archivo.getInputStream()) {
            return input.readNBytes(BYTES_FIRMA);
        }
    }

    // ¿El contenido del archivo corresponde al tipo MIME declarado?
    public static boolean contenidoCoincide(MultipartFile archivo, String tipoMime) throws IOException {
        return contenidoCoincide(tipoMime, leerInicio(archivo));
    }

    // Firmas de archivo: PDF "%PDF-", JPG FF D8 FF, PNG 89 50 4E 47, WEBP "RIFF....WEBP"
    public static boolean contenidoCoincide(String tipoMime, byte[] b) {
        if (tipoMime == null || b == null) {
            return false;
        }
        return switch (tipoMime) {
            case PDF -> empiezaCon(b, 0, '%', 'P', 'D', 'F', '-');
            case JPEG -> empiezaCon(b, 0, 0xFF, 0xD8, 0xFF);
            case PNG -> empiezaCon(b, 0, 0x89, 0x50, 0x4E, 0x47);
            case WEBP -> empiezaCon(b, 0, 'R', 'I', 'F', 'F') && empiezaCon(b, 8, 'W', 'E', 'B', 'P');
            default -> false;
        };
    }

    // ".pdf", ".jpg", ".png", ".webp" ("" si el tipo no es de los permitidos).
    public static String extension(String tipoMime) {
        if (tipoMime == null) {
            return "";
        }
        return switch (tipoMime.toLowerCase(Locale.ROOT)) {
            case PDF -> ".pdf";
            case JPEG -> ".jpg";
            case PNG -> ".png";
            case WEBP -> ".webp";
            default -> "";
        };
    }

    // Nombre seguro para guardar el archivo: UUID + extensión de su tipo real.
    public static String nombreAlmacenado(String tipoMime) {
        return UUID.randomUUID() + extension(tipoMime);
    }

    private static boolean empiezaCon(byte[] datos, int desde, int... firma) {
        if (datos.length < desde + firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if ((datos[desde + i] & 0xFF) != firma[i]) {
                return false;
            }
        }
        return true;
    }
}
