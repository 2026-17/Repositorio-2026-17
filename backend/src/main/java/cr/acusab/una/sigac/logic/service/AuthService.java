package cr.acusab.una.sigac.logic.service;



import cr.acusab.una.sigac.logic.security.JwtConfig;
import cr.acusab.una.sigac.presentation.dto.request.LoginRequest;
import cr.acusab.una.sigac.presentation.dto.response.LoginResponse;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtConfig jwtConfig;

    public AuthService(AuthenticationManager authenticationManager,  JwtConfig  jwtConfig) {
        this.authenticationManager = authenticationManager;
        this.jwtConfig = jwtConfig;
    }


    public LoginResponse login(LoginRequest loginRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getCedula(),
                        loginRequest.getContrasena()
                )
        );

        String token =  tokenGenerate(loginRequest.getCedula());

        return new LoginResponse(token, jwtConfig.getJwtExpiration());
    }

    private String tokenGenerate(String cedula) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plusMillis(jwtConfig.getJwtExpiration());

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(cedula)
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
