package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.TipoRequisitoCurso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoRequisitoCursoRepository extends JpaRepository<TipoRequisitoCurso, Short> {
    Optional<TipoRequisitoCurso> findByCodigoAndActivoTrue(String codigo);
}
