package cr.acusab.una.sigac.unit;

import cr.acusab.una.sigac.data.entity.EstadosUsuario;
import cr.acusab.una.sigac.data.entity.Role;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.EstadoUsuarioRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.security.UsuarioDetailsService;
import cr.acusab.una.sigac.logic.service.CuentaUsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * Una cuenta que no está ACTIVA (estudiante o profesor dado de baja) no puede
 * iniciar sesión, y la baja/reactivación cambia el estado de la cuenta.
 */
class CuentaInactivaTest {

    private static final String CONTRASENA = "Clave-Segura-1";

    private UsuarioRepository usuarioRepository;
    private EstadoUsuarioRepository estadoUsuarioRepository;
    private BCryptPasswordEncoder encoder;
    private EstadosUsuario activo;
    private EstadosUsuario inactivo;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        estadoUsuarioRepository = mock(EstadoUsuarioRepository.class);
        encoder = new BCryptPasswordEncoder(4);

        activo = estado(1, "ACTIVO");
        inactivo = estado(2, "INACTIVO");
        when(estadoUsuarioRepository.findByCodigoIgnoreCase("ACTIVO")).thenReturn(Optional.of(activo));
        when(estadoUsuarioRepository.findByCodigoIgnoreCase("INACTIVO")).thenReturn(Optional.of(inactivo));
    }

    @Test
    void usuarioInactivoQuedaDeshabilitado() {
        when(usuarioRepository.findByCedula("118760542")).thenReturn(Optional.of(usuario(inactivo)));

        var detalles = new UsuarioDetailsService(usuarioRepository).loadUserByUsername("118760542");

        assertFalse(detalles.isEnabled());
    }

    @Test
    void loginDeUsuarioInactivoLanzaDisabledException() {
        when(usuarioRepository.findByCedula("118760542")).thenReturn(Optional.of(usuario(inactivo)));

        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(new UsuarioDetailsService(usuarioRepository));
        proveedor.setPasswordEncoder(encoder);

        assertThrows(DisabledException.class, () -> proveedor.authenticate(
                new UsernamePasswordAuthenticationToken("118760542", CONTRASENA)));
    }

    @Test
    void usuarioActivoPuedeIniciarSesion() {
        when(usuarioRepository.findByCedula("118760542")).thenReturn(Optional.of(usuario(activo)));

        var detalles = new UsuarioDetailsService(usuarioRepository).loadUserByUsername("118760542");

        assertTrue(detalles.isEnabled());
    }

    @Test
    void inactivarYActivarCambianElEstadoDeLaCuenta() {
        CuentaUsuarioService cuentas = new CuentaUsuarioService(usuarioRepository, estadoUsuarioRepository);
        Usuario usuario = usuario(activo);

        cuentas.inactivar(usuario);
        assertEquals("INACTIVO", usuario.getIdEstadoUsuario().getCodigo());

        cuentas.activar(usuario);
        assertEquals("ACTIVO", usuario.getIdEstadoUsuario().getCodigo());
        verify(usuarioRepository, org.mockito.Mockito.times(2)).save(usuario);
    }

    private Usuario usuario(EstadosUsuario estado) {
        Role rol = new Role();
        rol.setCodigo("ESTUDIANTE");
        Usuario usuario = new Usuario();
        usuario.setId(10L);
        usuario.setCedula("118760542");
        usuario.setHashContrasena(encoder.encode(CONTRASENA));
        usuario.setIdRol(rol);
        usuario.setIdEstadoUsuario(estado);
        return usuario;
    }

    private EstadosUsuario estado(int id, String codigo) {
        EstadosUsuario estado = new EstadosUsuario();
        estado.setId(id);
        estado.setCodigo(codigo);
        return estado;
    }
}
