package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.ContactoEmergencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContactoEmergenciaRepository
        extends JpaRepository<ContactoEmergencia, Long> {

    List<ContactoEmergencia> findByEstudiante_IdAndActivoTrue(Long idEstudiante);

    List<ContactoEmergencia> findByEncargado_IdAndActivoTrue(Long idEncargado);

    List<ContactoEmergencia> findByEstudiante_IdAndEncargado_IdAndActivoTrue(
            Long idEstudiante,
            Long idEncargado
    );

    Optional<ContactoEmergencia> findByIdAndActivoTrue(Long id);

    long countByEstudiante_IdAndActivoTrue(Long idEstudiante);
}