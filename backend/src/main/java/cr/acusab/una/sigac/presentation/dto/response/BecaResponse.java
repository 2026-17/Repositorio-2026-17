package cr.acusab.una.sigac.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BecaResponse {

    private Long idBeca;

    private Long idEstudiante;
    private String nombreEstudiante;

    private Integer idTipoBeca;
    private String codigoTipoBeca;
    private String nombreTipoBeca;

    private Long idPeriodo;
    private String codigoPeriodo;

    private BigDecimal porcentaje;

    // Cálculo del descuento sobre la mensualidad del estudiante.
    private BigDecimal montoMensualidadBase;
    private BigDecimal montoDescuento;
    private BigDecimal montoAPagar;

    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Boolean activa;
    private String motivo;
}
