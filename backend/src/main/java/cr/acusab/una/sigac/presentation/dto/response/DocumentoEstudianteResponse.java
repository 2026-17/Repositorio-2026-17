package cr.acusab.una.sigac.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoEstudianteResponse {
    private Long id;
    private Long idEstudiante;
    private TipoDocumentoResponse tipoDocumento;
    private String nombreOriginal;
    private String tipoMime;
    private Long tamanoBytes;
    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;
    private Boolean esActual;
    private Instant cargadoEn;
}
