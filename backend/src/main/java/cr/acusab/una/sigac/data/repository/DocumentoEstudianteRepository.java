package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.DocumentosEstudiante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentoEstudianteRepository extends JpaRepository<DocumentosEstudiante, Long> {

    /*
     * Comprobante de matrícula vigente de un estudiante, usado al
     * generar/recalcular una preaprobación (Historia 1).
     */
    Optional<DocumentosEstudiante>
    findFirstByIdEstudiante_IdAndIdTipoDocumento_CodigoAndEsActualTrueOrderByCargadoEnDesc(
            Long idEstudiante,
            String codigoTipoDocumento
    );
}
