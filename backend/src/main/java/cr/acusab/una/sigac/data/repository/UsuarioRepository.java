package cr.acusab.una.sigac.data.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import cr.acusab.una.sigac.data.entity.Usuario;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;


public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByCedula(String cedula);

    // Usado por el filtro JWT en cada petición: solo trae el código del estado.
    @Query("SELECT u.idEstadoUsuario.codigo FROM Usuario u WHERE u.cedula = :cedula")
    Optional<String> findCodigoEstadoByCedula(@Param("cedula") String cedula);

    @Query("SELECT u FROM Usuario u WHERE u.correo = :correo")
    Optional<Usuario> findByCorreo(@Param("correo") String correo);

}
