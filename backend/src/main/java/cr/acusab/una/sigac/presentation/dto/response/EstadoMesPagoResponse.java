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
public class EstadoMesPagoResponse {

    private Integer anio;
    private Short mes;

    private Boolean pagada;

    // Presentes solo si ya esta pagada.
    private Long idMensualidad;
    private BigDecimal monto;
    private LocalDate fechaPago;

    // Monto que corresponde pagar ese mes (mensualidad base menos el
    // descuento de la beca activa, si tiene), para mostrarlo de una vez
    // sin que Administracion tenga que calcularlo ni digitarlo.
    private BigDecimal montoEsperado;

    private BigDecimal porcentajeBeca;

    // true cuando el estudiante tiene una beca del 100% para el
    // período consultado: no hay nada que cobrar ese mes.
    private boolean sinPagosPendientes;
}
