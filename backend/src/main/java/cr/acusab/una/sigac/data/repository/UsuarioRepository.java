package cr.acusab.una.sigac.data.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import cr.acusab.una.sigac.data.entity.Usuario;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByCedula(String cedula);

    // Usado por el filtro JWT en cada petición: solo trae el código del estado.
    @Query("SELECT u.idEstadoUsuario.codigo FROM Usuario u WHERE u.cedula = :cedula")
    Optional<String> findCodigoEstadoByCedula(@Param("cedula") String cedula);

    // Varias cuentas pueden compartir un correo: la llave de la cuenta es la cédula.
    // La intercalación de la columna (_ci) ya ignora mayúsculas y así se usa el índice.
    @Query("SELECT u FROM Usuario u JOIN FETCH u.idRol WHERE u.correo = :correo")
    List<Usuario> findAllByCorreoIgnoreCase(@Param("correo") String correo);

}
