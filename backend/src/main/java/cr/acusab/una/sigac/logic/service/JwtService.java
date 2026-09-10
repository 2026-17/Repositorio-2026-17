package cr.acusab.una.sigac.logic.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import cr.acusab.una.sigac.logic.exception.JwtInvalidoException;
import cr.acusab.una.sigac.logic.security.JwtConfig;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtConfig jwtConfig;

    public JwtService(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    public String generarToken(String cedula, String rolCodigo) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plusMillis(jwtConfig.getJwtExpiration());

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(cedula)
                    .claim("rol", rolCodigo)
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(jwtConfig.getAlgorithm()), claims);
            JWSSigner signer = new MACSigner(jwtConfig.getSecretKey().getEncoded());
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Error generating JWT token", e);
        }
    }

    public JWTClaimsSet validarYObtenerClaims(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(jwtConfig.getSecretKey().getEncoded());

            if (!signedJWT.verify(verifier)) {
                throw new JwtInvalidoException("Firma de token inválida");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.before(new Date())) {
                throw new JwtInvalidoException("Token expirado");
            }

            return claims;
        } catch (ParseException | JOSEException e) {
            throw new JwtInvalidoException("Token malformado", e);
        }
    }

    public long getExpirationMillis() {
        return jwtConfig.getJwtExpiration();
    }
}