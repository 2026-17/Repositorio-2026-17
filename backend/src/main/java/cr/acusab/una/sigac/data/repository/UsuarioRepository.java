package cr.acusab.una.sigac.data.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import cr.acusab.una.sigac.data.entity.Usuario;


public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {


}
