package cr.acusab.una.sigac.logic.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Configuration
public class JwtConfig {

    @Value("${security.jwt.secret-key}")
    private String secretKey;

    @Value("${security.jwt.expiration-time}")
    private long jwtExpiration;

    @Value("${security.jwt.algorithm}")
    private String algorithm;

    public SecretKey getSecretKey() {
        var key = new OctetSequenceKey.Builder(secretKey.getBytes(StandardCharsets.UTF_8))
                .algorithm(new JWSAlgorithm(algorithm))
                .build();
        return key.toSecretKey();
    }

    public long getJwtExpiration() {
        return jwtExpiration;
    }

    public JWSAlgorithm getAlgorithm() {
        return new JWSAlgorithm(algorithm);
    }
}
