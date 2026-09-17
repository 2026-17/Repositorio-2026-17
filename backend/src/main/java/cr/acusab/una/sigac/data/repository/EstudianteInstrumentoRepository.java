package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.EstudiantesInstrumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EstudianteInstrumentoRepository extends JpaRepository<EstudiantesInstrumento, Long> {
    @Query("""
            SELECT ei FROM EstudiantesInstrumento ei
            JOIN FETCH ei.idInstrumento
            WHERE ei.idEstudiante.id = :idEstudiante
              AND ei.esPrincipal = true
            """)
    Optional<EstudiantesInstrumento> buscarPrincipalPorEstudiante(@Param("idEstudiante") Long idEstudiante);
}
