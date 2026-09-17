package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Estudiante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {
    boolean existsByIdTipoIdentificacion_IdAndNumeroIdentificacion(
            Integer idTipoIdentificacion, String numeroIdentificacion);

    Optional<Estudiante> findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
            Integer idTipoIdentificacion, String numeroIdentificacion);

    @Query("""
            SELECT e FROM Estudiante e
            LEFT JOIN FETCH e.idCentroEducativo
            WHERE :busqueda IS NULL
               OR LOWER(CONCAT(e.nombres, ' ', e.primerApellido, ' ', COALESCE(e.segundoApellido, ''))) LIKE LOWER(CONCAT('%', :busqueda, '%'))
               OR LOWER(e.numeroIdentificacion) LIKE LOWER(CONCAT('%', :busqueda, '%'))
            ORDER BY e.primerApellido, e.segundoApellido, e.nombres
            """)
    List<Estudiante> buscarParaListado(@Param("busqueda") String busqueda);
}
