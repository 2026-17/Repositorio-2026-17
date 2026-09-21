package cr.acusab.una.sigac.data.repository;

import cr.acusab.una.sigac.data.entity.GradosAcademicos;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradoAcademicoRepository extends JpaRepository<GradosAcademicos, Integer> {
}
