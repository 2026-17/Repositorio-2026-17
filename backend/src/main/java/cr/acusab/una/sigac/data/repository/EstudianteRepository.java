package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Estudiante;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EstudianteRepository
        extends JpaRepository<Estudiante, Long> {

    boolean existsByIdTipoIdentificacion_IdAndNumeroIdentificacion(
            Integer idTipoIdentificacion,
            String numeroIdentificacion
    );

    Optional<Estudiante>
    findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
            Integer idTipoIdentificacion,
            String numeroIdentificacion
    );



    Optional<Estudiante> findByIdAndActivoTrue(Long id);

    /*
     * Código del tipo de estudiante (REGULAR o INCLUSION). Se lee con SQL
     * directo porque la entidad Estudiante de esta rama todavía no mapea
     * id_tipo_estudiante (está en f11/tipo-estudiante-inclusion). Necesita
     * db/cambios/2026-11-17_tipo_estudiante_inclusion.sql en la base.
     * Solo se usa con agrupaciones de "solo inclusión" (regla D6).
     */
    @Query(value = """
            SELECT te.codigo
            FROM estudiantes e
                     INNER JOIN tipos_estudiante te
                                ON te.id_tipo_estudiante = e.id_tipo_estudiante
            WHERE e.id_estudiante = :idEstudiante
            """, nativeQuery = true)
    Optional<String> findCodigoTipoEstudiante(@Param("idEstudiante") Long idEstudiante);
    Optional<Estudiante> findByUsuario_Cedula(String cedula);

    // Busqueda por nombre, apellidos o numero de identificacion,
    // usada en la pantalla de Administracion Academica para ubicar
    // al estudiante (beca y pagos).
    @Query("SELECT e FROM Estudiante e WHERE e.activo = true AND (" +
            "LOWER(e.nombres) LIKE LOWER(CONCAT('%', :texto, '%')) OR " +
            "LOWER(e.primerApellido) LIKE LOWER(CONCAT('%', :texto, '%')) OR " +
            "LOWER(e.segundoApellido) LIKE LOWER(CONCAT('%', :texto, '%')) OR " +
            "e.numeroIdentificacion LIKE CONCAT('%', :texto, '%')) " +
            "ORDER BY e.nombres ASC, e.primerApellido ASC")
    List<Estudiante> buscarPorNombreOIdentificacion(@Param("texto") String texto, Pageable pageable);
}
