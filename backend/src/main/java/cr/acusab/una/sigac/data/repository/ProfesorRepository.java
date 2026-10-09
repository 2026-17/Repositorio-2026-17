package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Profesor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfesorRepository extends JpaRepository<Profesor, Long> {

    // La identificacion es unica por tipo (uq_profesores_identificacion en la BD)
    Optional<Profesor> findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
            Integer idTipoIdentificacion, String numeroIdentificacion);

    List<Profesor> findByActivo(Boolean activo);

    List<Profesor> findByActivoTrueOrderByNombresAscPrimerApellidoAsc();

    // HU-026 - Profesores activos habilitados para dar cierto instrumento
    // (su campo especialidad, texto separado por comas, contiene el nombre
    // del instrumento). Se usa para el selector de profesor al asignar
    // un instrumento a un estudiante.
    List<Profesor> findByActivoTrueAndEspecialidadContainingIgnoreCase(String especialidad);
}
