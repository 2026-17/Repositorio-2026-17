package cr.acusab.una.sigac.presentation.controller;

import cr.acusab.una.sigac.logic.service.AuthService;
import cr.acusab.una.sigac.presentation.dto.request.CambiarContrasenaRequest;
import cr.acusab.una.sigac.presentation.dto.request.CambioContrasenaTemporalRequest;
import cr.acusab.una.sigac.presentation.dto.request.LoginRequest;
import cr.acusab.una.sigac.presentation.dto.request.RestablecerContrasenaRequest;
import cr.acusab.una.sigac.presentation.dto.request.SolicitarRecuperacionRequest;
import cr.acusab.una.sigac.presentation.dto.response.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/auth"})
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7));
        }
        return ResponseEntity.noContent().build();
    }

    // Requiere sesión. Es lo único (junto con logout) que permite un token
    // emitido con la contraseña temporal; responde con un token normal.
    @PostMapping("/contrasena-temporal/cambiar")
    public ResponseEntity<LoginResponse> cambiarContrasenaTemporal(
            Authentication authentication,
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CambioContrasenaTemporalRequest request) {
        return ResponseEntity.ok(authService.cambiarContrasenaTemporal(
                authentication.getName(), authHeader.substring(7), request));
    }

    // Público. Siempre responde lo mismo, exista o no la cédula.
    @PostMapping("/recuperar-contrasena/solicitar")
    public ResponseEntity<Map<String, String>> solicitarRecuperacion(
            @Valid @RequestBody SolicitarRecuperacionRequest request) {
        authService.solicitarCodigoRecuperacion(request.getCedula());
        return ResponseEntity.ok(Map.of("message",
                "Si la cédula está registrada, recibirá un código en el correo de la cuenta en unos minutos."));
    }

    // Público.
    @PostMapping("/recuperar-contrasena/confirmar")
    public ResponseEntity<Map<String, String>> restablecerContrasena(
            @Valid @RequestBody RestablecerContrasenaRequest request) {
        authService.restablecerContrasena(request);
        return ResponseEntity.ok(Map.of("message", "La contraseña se actualizó correctamente."));
    }

    // Requiere sesión: el usuario se toma del JWT (su cédula).
    @PostMapping("/cambiar-contrasena/solicitar")
    public ResponseEntity<Map<String, String>> solicitarCambio(Authentication authentication) {
        authService.solicitarCodigoCambio(authentication.getName());
        return ResponseEntity.ok(Map.of("message", "Se envió un código a su correo."));
    }

    // Requiere sesión.
    @PostMapping("/cambiar-contrasena/confirmar")
    public ResponseEntity<Map<String, String>> cambiarContrasena(
            Authentication authentication, @Valid @RequestBody CambiarContrasenaRequest request) {
        authService.cambiarContrasena(authentication.getName(), request);
        return ResponseEntity.ok(Map.of("message", "La contraseña se actualizó correctamente."));
    }
}
