package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.DocumentosEstudiante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoEstudianteRepository
        extends JpaRepository<DocumentosEstudiante, Long> {

    /*
     * Comprobante de matrícula vigente de un estudiante.
     * Método ya utilizado por otras funcionalidades del sistema.
     */
    Optional<DocumentosEstudiante>
    findFirstByIdEstudiante_IdAndIdTipoDocumento_CodigoAndEsActualTrueOrderByCargadoEnDesc(
            Long idEstudiante,
            String codigoTipoDocumento
    );

    /*
     * HU-022:
     * Obtiene el documento vigente de un estudiante para un tipo
     * determinado. Se utiliza para poder reemplazarlo.
     */
    Optional<DocumentosEstudiante>
    findFirstByIdEstudiante_IdAndIdTipoDocumento_IdAndEsActualTrueOrderByCargadoEnDesc(
            Long idEstudiante,
            Integer idTipoDocumento
    );

    /*
     * HU-022:
     * Lista únicamente los documentos vigentes del expediente
     * del estudiante.
     */
    List<DocumentosEstudiante>
    findByIdEstudiante_IdAndEsActualTrueOrderByCargadoEnDesc(
            Long idEstudiante
    );
}