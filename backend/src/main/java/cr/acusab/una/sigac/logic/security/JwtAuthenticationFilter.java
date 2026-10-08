package cr.acusab.una.sigac.logic.security;

import com.nimbusds.jwt.JWTClaimsSet;
import cr.acusab.una.sigac.data.repository.TokenRevocadoRepository;
import cr.acusab.una.sigac.logic.exception.JwtInvalidoException;
import cr.acusab.una.sigac.logic.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> RUTAS_CON_CAMBIO_PENDIENTE =
            Set.of("/auth/contrasena-temporal/cambiar", "/auth/logout");

    private final JwtService jwtService;
    private final TokenRevocadoRepository tokenRevocadoRepository;

    public JwtAuthenticationFilter(JwtService jwtService, TokenRevocadoRepository tokenRevocadoRepository) {
        this.jwtService = jwtService;
        this.tokenRevocadoRepository = tokenRevocadoRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JWTClaimsSet claims = jwtService.validarYObtenerClaims(header.substring(7));
            String jti = claims.getJWTID();
            if (jti == null || !tokenRevocadoRepository.existsByJti(jti)) {
                // Token emitido con la contraseña temporal: solo sirve para cambiarla.
                // Se corta aquí (y no en SecurityConfig) para que cubra también los
                // endpoints que solo piden estar autenticado.
                if (Boolean.TRUE.equals(claims.getClaim(JwtService.CLAIM_CAMBIO_CONTRASENA))
                        && !RUTAS_CON_CAMBIO_PENDIENTE.contains(request.getServletPath())) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"status\":403,\"message\":"
                            + "\"Debe cambiar su contraseña temporal antes de usar el sistema.\"}");
                    return;
                }

                String cedula = claims.getSubject();
                String rol = (String) claims.getClaim("rol");
                List<SimpleGrantedAuthority> authorities = rol != null
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase()))
                        : List.of();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(cedula, null, authorities));
            }
        } catch (JwtInvalidoException ignored) {
            // La petición continúa sin autenticación.
        }
        filterChain.doFilter(request, response);
    }
}
