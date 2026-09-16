package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Estudiante;
import org.springframework.data.jpa.repository.JpaRepository;

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

    Optional<Estudiante> findByIdAndActivoTrue(
            Long id
    );

    /*
     * Permite identificar al estudiante correspondiente
     * al usuario que inició sesión.
     *
     * El JWT utiliza la cédula como nombre de autenticación.
     */
    Optional<Estudiante> findByUsuario_Cedula(
            String cedula
    );
}