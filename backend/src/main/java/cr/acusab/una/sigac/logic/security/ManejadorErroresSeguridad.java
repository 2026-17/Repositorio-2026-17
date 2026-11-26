package cr.acusab.una.sigac.logic.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/*
 * Respuestas de Spring Security cuando una ruta protegida no se puede usar.
 *
 * - Sin autenticación (entry point): 401. Si la petición traía un token que
 *   ya no sirve (JwtAuthenticationFilter la marcó), el código es
 *   SESION_EXPIRADA; si no traía token, SIN_SESION. El frontend usa el 401
 *   para avisar y llevar a iniciar sesión.
 * - Autenticado pero sin el rol (access denied): 403.
 *
 * Las rutas permitAll nunca llegan aquí: un token vencido en una ruta
 * pública no impide usarla.
 */
@Component
public class ManejadorErroresSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String CUERPO_SESION_EXPIRADA = "{\"status\":401,\"codigo\":\"SESION_EXPIRADA\","
            + "\"message\":\"Su sesión expiró. Inicie sesión de nuevo.\"}";
    static final String CUERPO_SIN_SESION = "{\"status\":401,\"codigo\":\"SIN_SESION\","
            + "\"message\":\"Debe iniciar sesión.\"}";
    static final String CUERPO_SIN_PERMISO = "{\"status\":403,"
            + "\"message\":\"No tiene permisos para realizar esta acción.\"}";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean sesionInvalida = Boolean.TRUE.equals(
                request.getAttribute(JwtAuthenticationFilter.ATRIBUTO_SESION_INVALIDA));
        escribir(response, HttpServletResponse.SC_UNAUTHORIZED,
                sesionInvalida ? CUERPO_SESION_EXPIRADA : CUERPO_SIN_SESION);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        escribir(response, HttpServletResponse.SC_FORBIDDEN, CUERPO_SIN_PERMISO);
    }

    private static void escribir(HttpServletResponse response, int status, String cuerpo) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(cuerpo);
    }
}
