package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.DocumentosEstudiante;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoEstudianteRepository extends JpaRepository<DocumentosEstudiante, Long> {
}
