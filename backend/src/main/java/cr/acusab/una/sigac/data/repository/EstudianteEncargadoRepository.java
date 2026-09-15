package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.EstudianteEncargado;
import cr.acusab.una.sigac.data.entity.EstudianteEncargadoId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EstudianteEncargadoRepository
        extends JpaRepository<EstudianteEncargado, EstudianteEncargadoId> {

    List<EstudianteEncargado> findByIdEstudiante(Long idEstudiante);

    Optional<EstudianteEncargado> findByIdEstudianteAndIdEncargado(
            Long idEstudiante,
            Long idEncargado
    );

    boolean existsByIdEstudianteAndIdEncargado(
            Long idEstudiante,
            Long idEncargado
    );

    long countByIdEstudiante(Long idEstudiante);
}