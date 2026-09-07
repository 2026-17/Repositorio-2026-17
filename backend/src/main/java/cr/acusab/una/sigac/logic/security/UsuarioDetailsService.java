package cr.acusab.una.sigac.logic.security;

import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String cedula) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByCedula(cedula)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));

        boolean bloqueado = usuario.getBloqueadoHasta() != null
                && usuario.getBloqueadoHasta().isAfter(Instant.now());

        String codigoRol = usuario.getIdRol().getCodigo().toUpperCase(Locale.ROOT);

        return User.builder()
                .username(cedula)
                .password(usuario.getHashContrasena())
                .accountLocked(bloqueado)
                .disabled(false)
                .authorities("ROLE_" + codigoRol)
                .build();
    }
}
