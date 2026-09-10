package cr.acusab.una.sigac.logic.service;



import cr.acusab.una.sigac.data.entity.Role;
import cr.acusab.una.sigac.data.entity.Usuario;
import cr.acusab.una.sigac.data.repository.RoleRepository;
import cr.acusab.una.sigac.data.repository.UsuarioRepository;
import cr.acusab.una.sigac.logic.security.JwtConfig;
import cr.acusab.una.sigac.presentation.dto.request.LoginRequest;
import cr.acusab.una.sigac.presentation.dto.response.LoginResponse;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Date;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtConfig jwtConfig;
    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;

    public AuthService(AuthenticationManager authenticationManager, JwtConfig jwtConfig,
                        UsuarioRepository usuarioRepository, RoleRepository roleRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtConfig = jwtConfig;
        this.usuarioRepository = usuarioRepository;
        this.roleRepository = roleRepository;
    }

    public LoginResponse login(LoginRequest loginRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getCedula(),
                        loginRequest.getContrasena()
                )
        );

        Usuario usuario = usuarioRepository.findByCedula(loginRequest.getCedula())
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));

        // idRol es LAZY y open-in-view está deshabilitado: el proxy solo trae el id,
        // por eso se busca el Role completo (con su código) mediante RoleRepository.
        Role rol = roleRepository.findById(usuario.getIdRol().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol no existe"));
        String rolCodigo = rol.getCodigo();

        String token = tokenGenerate(loginRequest.getCedula(), rolCodigo);

        return new LoginResponse(token, jwtConfig.getJwtExpiration(), rolCodigo);
    }

    private String tokenGenerate(String cedula, String rolCodigo) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plusMillis(jwtConfig.getJwtExpiration());

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(cedula)
                    .claim("rol", rolCodigo)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(jwtConfig.getAlgorithm())
                    ,claims
            );

            JWSSigner signer = new MACSigner(jwtConfig.getSecretKey().getEncoded());
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Error generating JWT token", e);
        }
    }

}
