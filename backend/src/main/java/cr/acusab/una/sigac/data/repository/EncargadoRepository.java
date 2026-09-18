package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Encargado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EncargadoRepository extends JpaRepository<Encargado, Long> {

    Optional<Encargado> findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
            Integer idTipoIdentificacion,
            String numeroIdentificacion
    );

    Optional<Encargado> findByIdAndActivoTrue(Long id);
}