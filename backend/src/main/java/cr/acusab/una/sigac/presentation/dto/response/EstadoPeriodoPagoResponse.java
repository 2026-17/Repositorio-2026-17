package cr.acusab.una.sigac.presentation.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/*
 * Estado de pagos de un estudiante en todo un período: la matrícula y
 * cada mes del período, para mostrarlos como casillas en "Becas y pagos".
 */
@Getter
@Setter
@NoArgsConstructor
public class EstadoPeriodoPagoResponse {

    private Long idPeriodo;

    // Monto de cada mensualidad pendiente (ya con el descuento de beca).
    private BigDecimal montoEsperado;

    private BigDecimal porcentajeBeca;

    // true con beca del 100%: las mensualidades no se cobran (la matrícula sí).
    private boolean sinPagosPendientes;

    private EstadoMatriculaPago matricula = new EstadoMatriculaPago();

    private List<EstadoMesPagoResponse> meses = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class EstadoMatriculaPago {

        private boolean pagada;

        // Solicitud de matrícula del período que guarda el comprobante.
        private Long idAutorizacion;

        private LocalDate fechaPago;

        // Solo se puede quitar mientras la solicitud siga pendiente.
        private boolean eliminable;
    }
}
