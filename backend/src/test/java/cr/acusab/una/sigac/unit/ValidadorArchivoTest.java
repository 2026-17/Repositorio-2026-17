package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.logic.util.ValidadorArchivo;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Validación de archivos PDF o imagen (comprobantes de pago y póliza):
 * tipo MIME permitido, firma de los primeros bytes y nombre seguro.
 */
class ValidadorArchivoTest {

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P'};
    private static final byte[] PDF = "%PDF-1.7 contenido".getBytes();

    @Test
    void tiposPermitidos_sonPdfJpgPngYWebp() {
        assertTrue(ValidadorArchivo.esPdfOImagen("application/pdf"));
        assertTrue(ValidadorArchivo.esPdfOImagen("image/jpeg"));
        assertTrue(ValidadorArchivo.esPdfOImagen("image/png"));
        assertTrue(ValidadorArchivo.esPdfOImagen("image/webp"));
        assertFalse(ValidadorArchivo.esPdfOImagen("image/gif"));
        assertFalse(ValidadorArchivo.esPdfOImagen("text/plain"));
        assertFalse(ValidadorArchivo.esImagen("application/pdf"));
        assertTrue(ValidadorArchivo.esImagen("image/webp"));
    }

    @Test
    void firmas_reconocenCadaFormato() {
        assertTrue(ValidadorArchivo.contenidoCoincide("application/pdf", PDF));
        assertTrue(ValidadorArchivo.contenidoCoincide("image/jpeg", JPG));
        assertTrue(ValidadorArchivo.contenidoCoincide("image/png", PNG));
        assertTrue(ValidadorArchivo.contenidoCoincide("image/webp", WEBP));
    }

    @Test
    void firmas_rechazanContenidoQueNoCoincide() {
        assertFalse(ValidadorArchivo.contenidoCoincide("image/jpeg", PDF));
        assertFalse(ValidadorArchivo.contenidoCoincide("application/pdf", JPG));
        assertFalse(ValidadorArchivo.contenidoCoincide("image/png", "texto".getBytes()));
        assertFalse(ValidadorArchivo.contenidoCoincide("image/webp", new byte[]{'R', 'I', 'F', 'F'}));
        assertFalse(ValidadorArchivo.contenidoCoincide("image/jpeg", new byte[0]));
    }

    @Test
    void tipoMime_seNormalizaYLeeElArchivo() throws IOException {
        MockMultipartFile archivo = new MockMultipartFile("a", "foto.JPG", "IMAGE/JPEG", JPG);

        assertEquals("image/jpeg", ValidadorArchivo.tipoMime(archivo));
        assertTrue(ValidadorArchivo.contenidoCoincide(archivo, "image/jpeg"));
    }

    @Test
    void nombreAlmacenado_usaUuidYLaExtensionDelTipoReal() {
        String nombre = ValidadorArchivo.nombreAlmacenado("image/png");

        assertTrue(nombre.matches("[0-9a-f-]{36}\\.png"), nombre);
        assertEquals(".pdf", ValidadorArchivo.extension("application/pdf"));
        assertEquals(".jpg", ValidadorArchivo.extension("image/jpeg"));
        assertEquals(".webp", ValidadorArchivo.extension("image/webp"));
        assertEquals("", ValidadorArchivo.extension("text/plain"));
    }
}
