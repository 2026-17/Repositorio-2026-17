package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Matricula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatriculaRepository
        extends JpaRepository<Matricula, Long> {

    /*
     * Historia 2 - Matrícula del estudiante en el período. Solo puede
     * existir una por autorización.
     */
    Optional<Matricula> findByAutorizacion_Id(Long idAutorizacion);

    @Query("""
            SELECT m
            FROM Matricula m
            WHERE m.autorizacion.idEstudiante.id = :idEstudiante
              AND LOWER(m.estado.codigo) = LOWER(:codigoEstado)
            """)
    List<Matricula> findByEstudianteAndEstado(
            @Param("idEstudiante") Long idEstudiante,
            @Param("codigoEstado") String codigoEstado
    );
}
