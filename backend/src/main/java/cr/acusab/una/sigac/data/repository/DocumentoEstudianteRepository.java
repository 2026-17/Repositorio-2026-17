package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.DocumentoEstudiante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoEstudianteRepository extends JpaRepository<DocumentoEstudiante, Long> {

    // Documentos vigentes del expediente de un estudiante.
    List<DocumentoEstudiante> findByIdEstudiante_IdAndEsActualTrue(Long idEstudiante);

    // Documento vigente de un tipo especifico, para saber si hay que reemplazarlo.
    Optional<DocumentoEstudiante> findByIdEstudiante_IdAndIdTipoDocumento_IdAndEsActualTrue(
            Long idEstudiante, Integer idTipoDocumento);
}
