package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Beca;
import cr.acusab.una.sigac.data.repository.BecaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/*
 * Único punto donde se decide qué beca le aplica a un estudiante en un
 * período. Lo usan los pagos, la autorización de matrícula y las becas,
 * para que todas las pantallas calculen lo mismo.
 *
 * Regla: si el estudiante tiene la beca permanente de inclusión activa,
 * esa es la vigente (cubre todos los períodos). Si no, se usa la beca
 * activa asignada a ese período (HU-015).
 */
@Service
public class BecaVigenteService {

    public static final String CODIGO_TIPO_BECA_INCLUSION = "INCLUSION";
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final BecaRepository becaRepository;

    public BecaVigenteService(BecaRepository becaRepository) {
        this.becaRepository = becaRepository;
    }

    /*
     * Beca que aplica al estudiante en el período indicado. idPeriodo puede
     * ser null (ej. un pago viejo sin período): en ese caso solo cuenta la
     * beca permanente.
     */
    @Transactional(readOnly = true)
    public Optional<Beca> obtenerBecaVigente(Long idEstudiante, Long idPeriodo) {
        Optional<Beca> permanente = obtenerBecaPermanenteActiva(idEstudiante);
        if (permanente.isPresent() || idPeriodo == null) {
            return permanente;
        }
        return becaRepository.findByIdEstudiante_IdAndIdPeriodo_IdAndActivaTrue(idEstudiante, idPeriodo);
    }

    @Transactional(readOnly = true)
    public Optional<Beca> obtenerBecaPermanenteActiva(Long idEstudiante) {
        return becaRepository
                .findByIdEstudiante_IdAndIdTipoBeca_CodigoAndIdPeriodoIsNullAndActivaTrue(
                        idEstudiante, CODIGO_TIPO_BECA_INCLUSION)
                .stream()
                .findFirst();
    }

    // true si la beca cubre el 100% de la mensualidad.
    public static boolean cubreTotalidad(Beca beca) {
        return beca != null
                && beca.getPorcentaje() != null
                && beca.getPorcentaje().compareTo(CIEN) == 0;
    }

    // La beca permanente de inclusión: tipo INCLUSION y sin período.
    public static boolean esBecaPermanenteInclusion(Beca beca) {
        return beca != null
                && beca.getIdPeriodo() == null
                && beca.getIdTipoBeca() != null
                && CODIGO_TIPO_BECA_INCLUSION.equalsIgnoreCase(beca.getIdTipoBeca().getCodigo());
    }
}
