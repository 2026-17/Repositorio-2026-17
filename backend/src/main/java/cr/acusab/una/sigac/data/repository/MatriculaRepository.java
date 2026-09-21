package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.Matricula;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatriculaRepository
        extends JpaRepository<Matricula, Long> {

    /*
     * Historia 2 - Matrícula del estudiante en el período. Solo puede
     * existir una por autorización.
     */
    Optional<Matricula> findByAutorizacion_Id(Long idAutorizacion);
}
