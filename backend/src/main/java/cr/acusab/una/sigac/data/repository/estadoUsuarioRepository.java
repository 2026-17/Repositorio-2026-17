package cr.acusab.una.sigac.data.repository;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.FluentQuery;
import cr.acusab.una.sigac.data.entity.EstadosUsuario;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface estadoUsuarioRepository extends JpaRepository<EstadosUsuario, Integer> {

}
