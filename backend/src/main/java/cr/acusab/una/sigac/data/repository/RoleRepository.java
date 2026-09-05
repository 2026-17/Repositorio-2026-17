package cr.acusab.una.sigac.data.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import cr.acusab.una.sigac.data.entity.Role;

public interface RoleRepository extends JpaRepository<Role, Integer> {
}
