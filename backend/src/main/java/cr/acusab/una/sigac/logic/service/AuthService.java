package cr.acusab.una.sigac.logic.service;

import com.nimbusds.jwt.JWTClaimsSet;
import cr.acusab.una.sigac.data.entity.Role;
import cr.acusab.una.sigac.data.entity.TokensRevocado;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.RoleRepository;
import cr.acusab.una.sigac.data.repository.TokenRevocadoRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.presentation.dto.request.LoginRequest;
import cr.acusab.una.sigac.presentation.dto.response.LoginResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final TokenRevocadoRepository tokenRevocadoRepository;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService,
                       UsuarioRepository usuarioRepository, RoleRepository roleRepository,
                       TokenRevocadoRepository tokenRevocadoRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.roleRepository = roleRepository;
        this.tokenRevocadoRepository = tokenRevocadoRepository;
    }

    public LoginResponse login(LoginRequest loginRequest) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                loginRequest.getCedula(), loginRequest.getContrasena()));

        Usuario usuario = usuarioRepository.findByCedula(loginRequest.getCedula())
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
        Role rol = roleRepository.findById(usuario.getIdRol().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no existe"));

        String token = jwtService.generarToken(loginRequest.getCedula(), rol.getCodigo());

        return new LoginResponse(token, jwtService.getExpirationMillis(), rol.getCodigo(), usuario.getNombre(), usuario.getCorreo());
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
}
