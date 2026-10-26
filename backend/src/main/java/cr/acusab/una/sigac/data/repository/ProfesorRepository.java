package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Profesor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ProfesorRepository extends JpaRepository<Profesor, Long> {

    // La identificacion es unica por tipo (uq_profesores_identificacion en la BD)
    Optional<Profesor> findByIdTipoIdentificacion_IdAndNumeroIdentificacion(
            Integer idTipoIdentificacion, String numeroIdentificacion);

    List<Profesor> findByActivo(Boolean activo);

    /*
     * HU-014: serializa asignaciones concurrentes al mismo profesor para
     * reducir el riesgo de aceptar dos asignaciones incompatibles al mismo tiempo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Profesor p WHERE p.id = :id")
    Optional<Profesor> findByIdParaAsignacion(@Param("id") Long id);


    List<Profesor> findByActivoTrueOrderByNombresAscPrimerApellidoAsc();

    // Catálogo completo (activos e inactivos) para la pantalla de asignación.
    List<Profesor> findAllByOrderByNombresAscPrimerApellidoAsc();

    // HU-026 - Profesores activos habilitados para dar cierto instrumento
    // (su campo especialidad, texto separado por comas, contiene el nombre
    // del instrumento). Se usa para el selector de profesor al asignar
    // un instrumento a un estudiante.
    List<Profesor> findByActivoTrueAndEspecialidadContainingIgnoreCase(String especialidad);
}
