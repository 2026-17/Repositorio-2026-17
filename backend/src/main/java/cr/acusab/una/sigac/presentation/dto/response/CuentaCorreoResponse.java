package cr.acusab.una.sigac.presentation.dto.response;

// Cuenta que ya usa un correo. La cédula va enmascarada: solo los últimos 4 dígitos.
public record CuentaCorreoResponse(String nombreCompleto, String cedulaEnmascarada, String rol) {}
