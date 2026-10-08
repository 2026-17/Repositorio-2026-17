package cr.acusab.una.sigac.logic.service;

import com.nimbusds.jwt.JWTClaimsSet;
import cr.acusab.una.sigac.data.entity.CodigoVerificacion.Proposito;
import cr.acusab.una.sigac.data.entity.Role;
import cr.acusab.una.sigac.data.entity.TokensRevocado;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.RoleRepository;
import cr.acusab.una.sigac.data.repository.TokenRevocadoRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.presentation.dto.request.CambiarContrasenaRequest;
import cr.acusab.una.sigac.presentation.dto.request.CambioContrasenaTemporalRequest;
import cr.acusab.una.sigac.presentation.dto.request.LoginRequest;
import cr.acusab.una.sigac.presentation.dto.request.RestablecerContrasenaRequest;
import cr.acusab.una.sigac.presentation.dto.response.LoginResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final TokenRevocadoRepository tokenRevocadoRepository;
    private final CodigoVerificacionService codigoVerificacionService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService,
                       UsuarioRepository usuarioRepository, RoleRepository roleRepository,
                       TokenRevocadoRepository tokenRevocadoRepository,
                       CodigoVerificacionService codigoVerificacionService,
                       EmailService emailService, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.roleRepository = roleRepository;
        this.tokenRevocadoRepository = tokenRevocadoRepository;
        this.codigoVerificacionService = codigoVerificacionService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest loginRequest) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                loginRequest.getCedula(), loginRequest.getContrasena()));

        Usuario usuario = usuarioRepository.findByCedula(loginRequest.getCedula())
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
        Role rol = roleRepository.findById(usuario.getIdRol().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no existe"));

        return respuestaLogin(usuario, rol);
    }

    /**
     * Primer inicio de sesión: cambia la contraseña temporal por la del
     * usuario. El token con el que se llama (que solo servía para esto) se
     * revoca, y se devuelve uno nuevo con acceso normal según el rol.
     */
    @Transactional
    public LoginResponse cambiarContrasenaTemporal(String cedula, String token, CambioContrasenaTemporalRequest request) {
        Usuario usuario = buscarPorCedula(cedula);

        if (!Boolean.TRUE.equals(usuario.getRequiereCambioContrasena())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Su cuenta no tiene un cambio de contraseña pendiente.");
        }
        if (!passwordEncoder.matches(request.getContrasenaActual(), usuario.getHashContrasena())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña temporal no es correcta.");
        }
        if (passwordEncoder.matches(request.getContrasenaNueva(), usuario.getHashContrasena())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La nueva contraseña debe ser distinta de la contraseña temporal.");
        }
        guardarNuevaContrasena(usuario, request.getContrasenaNueva());
        logout(token);

        Role rol = roleRepository.findById(usuario.getIdRol().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no existe"));
        return respuestaLogin(usuario, rol);
    }

    private LoginResponse respuestaLogin(Usuario usuario, Role rol) {
        boolean requiereCambio = Boolean.TRUE.equals(usuario.getRequiereCambioContrasena());
        String token = jwtService.generarToken(usuario.getCedula(), rol.getCodigo(), requiereCambio);
        return new LoginResponse(token, jwtService.getExpirationMillis(), rol.getCodigo(),
                usuario.getNombre(), usuario.getCorreo(), requiereCambio);
    }


    public void logout(String token) {
        JWTClaimsSet claims = jwtService.validarYObtenerClaims(token);
        String jti = claims.getJWTID();
        if (jti == null || tokenRevocadoRepository.existsByJti(jti)) return;

        Usuario usuario = usuarioRepository.findByCedula(claims.getSubject())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        TokensRevocado revocado = new TokensRevocado();
        revocado.setJti(jti);
        revocado.setIdUsuario(usuario);
        revocado.setExpiraEn(claims.getExpirationTime().toInstant());
        revocado.setRevocadoEn(Instant.now());
        tokenRevocadoRepository.save(revocado);
    }

    /**
     * "Olvidé mi contraseña", paso 1. El código se envía al correo de la cuenta.
     * Nunca revela si la cédula existe: si no existe, o si el envío falla, no se
     * avisa al usuario (solo queda en el log).
     */
    public void solicitarCodigoRecuperacion(String cedula) {
        usuarioRepository.findByCedula(cedula.trim()).ifPresent(usuario -> {
            try {
                String codigo = codigoVerificacionService.generar(usuario, Proposito.RECUPERACION);
                emailService.enviarCodigoRecuperacion(usuario.getCorreo(), codigo,
                        CodigoVerificacionService.MINUTOS_VIGENCIA);
            } catch (ResponseStatusException e) {
                log.warn("No se envió el código de recuperación al usuario {}: {}", usuario.getId(), e.getReason());
            }
        });
    }

    /** "Olvidé mi contraseña", paso 2: valida el código y guarda la nueva contraseña. */
    @Transactional
    public void restablecerContrasena(RestablecerContrasenaRequest request) {
        Usuario usuario = usuarioRepository.findByCedula(request.getCedula().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        CodigoVerificacionService.CODIGO_INVALIDO));

        codigoVerificacionService.validar(usuario, Proposito.RECUPERACION, request.getCodigo());
        guardarNuevaContrasena(usuario, request.getContrasenaNueva());
    }

    /** Cambio con sesión iniciada, paso 1: envía un código al correo del usuario. */
    public void solicitarCodigoCambio(String cedula) {
        Usuario usuario = buscarPorCedula(cedula);
        String codigo = codigoVerificacionService.generar(usuario, Proposito.CAMBIO);
        emailService.enviarCodigoCambio(usuario.getCorreo(), codigo, CodigoVerificacionService.MINUTOS_VIGENCIA);
    }

    /**
     * Cambio con sesión iniciada, paso 2. El código se valida ANTES que la
     * contraseña actual: así cada intento de adivinar la contraseña gasta un
     * código, y los códigos están limitados a 5 por hora.
     */
    @Transactional
    public void cambiarContrasena(String cedula, CambiarContrasenaRequest request) {
        Usuario usuario = buscarPorCedula(cedula);

        codigoVerificacionService.validar(usuario, Proposito.CAMBIO, request.getCodigo());

        if (!passwordEncoder.matches(request.getContrasenaActual(), usuario.getHashContrasena())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña actual no es correcta. Solicite un código nuevo e intente de nuevo.");
        }
        guardarNuevaContrasena(usuario, request.getContrasenaNueva());
    }

    private Usuario buscarPorCedula(String cedula) {
        return usuarioRepository.findByCedula(cedula)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private void guardarNuevaContrasena(Usuario usuario, String contrasenaNueva) {
        if (passwordEncoder.matches(contrasenaNueva, usuario.getHashContrasena())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La nueva contraseña debe ser distinta de la actual. Solicite un código nuevo.");
        }
        usuario.setHashContrasena(passwordEncoder.encode(contrasenaNueva));
        usuario.setRequiereCambioContrasena(false);
        usuario.setIntentosFallidos((short) 0);
        usuario.setBloqueadoHasta(null);
        usuario.setActualizadoEn(Instant.now());
        usuarioRepository.save(usuario);
    }
}
