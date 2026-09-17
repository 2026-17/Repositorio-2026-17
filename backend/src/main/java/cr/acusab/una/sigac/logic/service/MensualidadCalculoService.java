package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.repository.EstudianteInstrumentoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/*
 * Calculo compartido del monto de mensualidad de un estudiante y del
 * descuento que le corresponde segun su beca. Lo usan tanto la
 * asignacion de becas (HU-015) como el registro de pagos, para que
 * ambas pantallas muestren siempre el mismo monto.
 */
@Service
public class MensualidadCalculoService {

    // Mensualidad base y costo por cada instrumento adicional al primero.
    private static final BigDecimal MONTO_BASE_MENSUALIDAD = new BigDecimal("27000");
    private static final BigDecimal MONTO_INSTRUMENTO_EXTRA = new BigDecimal("15000");

    private final EstudianteInstrumentoRepository estudianteInstrumentoRepository;

    public MensualidadCalculoService(EstudianteInstrumentoRepository estudianteInstrumentoRepository) {
        this.estudianteInstrumentoRepository = estudianteInstrumentoRepository;
    }

    public BigDecimal calcularMontoBase(Long idEstudiante) {

        long cantidadInstrumentos = estudianteInstrumentoRepository
                .countByIdEstudiante_IdAndFechaFinIsNull(idEstudiante);

        long instrumentosExtra = Math.max(0, cantidadInstrumentos - 1);

        return MONTO_BASE_MENSUALIDAD
                .add(MONTO_INSTRUMENTO_EXTRA.multiply(BigDecimal.valueOf(instrumentosExtra)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calcularDescuento(BigDecimal montoBase, BigDecimal porcentajeBeca) {
        if (porcentajeBeca == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return montoBase
                .multiply(porcentajeBeca)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calcularMontoNeto(BigDecimal montoBase, BigDecimal porcentajeBeca) {
        return montoBase.subtract(calcularDescuento(montoBase, porcentajeBeca));
    }
}
