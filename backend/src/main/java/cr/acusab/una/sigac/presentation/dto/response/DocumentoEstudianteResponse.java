package cr.acusab.una.sigac.presentation.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/**
 * HU-022 - Información pública de un documento asociado
 * al expediente de un estudiante.
 *
 * No se exponen la ruta física, el nombre interno ni el hash
 * del archivo.
 */
public record DocumentoEstudianteResponse(
        Long id,
        Long idEstudiante,
        Integer idTipoDocumento,
        String codigoTipoDocumento,
        String nombreTipoDocumento,
        String nombreOriginal,
        String tipoMime,
        Long tamanoBytes,
        LocalDate fechaEmision,
        LocalDate fechaVencimiento,
        Boolean esActual,
        Instant cargadoEn
) {
}