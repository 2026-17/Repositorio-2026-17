package cr.acusab.una.sigac.data.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import cr.acusab.una.sigac.data.entity.EstadosUsuario;

import java.util.Optional;

public interface EstadoUsuarioRepository extends JpaRepository<EstadosUsuario, Integer> {
    Optional<EstadosUsuario> findByCodigoIgnoreCase(String codigo);
}