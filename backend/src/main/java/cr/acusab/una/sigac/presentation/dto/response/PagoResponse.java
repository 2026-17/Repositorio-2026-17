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
public class PagoResponse {

    private Long idMensualidad;

    private Long idEstudiante;
    private String nombreEstudiante;

    private Long idPeriodo;
    private String codigoPeriodo;

    private Integer anio;
    private Short mes;

    private BigDecimal monto;
    private LocalDate fechaPago;

    // Monto neto esperado para esa mensualidad (ya con el descuento de
    // beca aplicado), para que Administracion pueda comparar contra
    // el monto realmente pagado.
    private BigDecimal montoEsperado;

    private Boolean pagada;
    private String mensaje;
}
