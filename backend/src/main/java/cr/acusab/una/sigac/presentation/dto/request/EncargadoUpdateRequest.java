package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static cr.acusab.una.sigac.presentation.dto.request.PoliticaDatosPersona.*;

import java.util.List;

/**
 * Datos utilizados para modificar un encargado asociado
 * a un estudiante en la HU-023 Registrar encargados.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EncargadoUpdateRequest {

    @NotNull(message = "El tipo de identificación es obligatorio")
    private Integer idTipoIdentificacion;

    @NotBlank(message = "El número de identificación es obligatorio")
    @Size(max = 40, message = "El número de identificación no puede superar 40 caracteres")
    private String numeroIdentificacion;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    @Pattern(regexp = NOMBRE_REGEX, message = NOMBRE_MENSAJE)
    private String nombres;

    @NotBlank(message = "El primer apellido es obligatorio")
    @Size(max = 80, message = "El primer apellido no puede superar 80 caracteres")
    @Pattern(regexp = NOMBRE_REGEX, message = NOMBRE_MENSAJE)
    private String primerApellido;

    @Size(max = 80, message = "El segundo apellido no puede superar 80 caracteres")
    @Pattern(regexp = NOMBRE_OPCIONAL_REGEX, message = NOMBRE_MENSAJE)
    private String segundoApellido;

    @NotBlank(message = "El teléfono principal es obligatorio")
    @Pattern(regexp = TELEFONO_REGEX, message = TELEFONO_MENSAJE)
    private String telefonoPrincipal;

    @Pattern(regexp = TELEFONO_OPCIONAL_REGEX, message = TELEFONO_MENSAJE)
    private String telefonoSecundario;

    @Email(message = CORREO_MENSAJE)
    @Pattern(regexp = CORREO_OPCIONAL_REGEX, message = CORREO_MENSAJE)
    @Size(max = 254, message = "El correo no puede superar 254 caracteres")
    private String correo;

    @Size(max = 120, message = "La ocupación no puede superar 120 caracteres")
    private String ocupacion;

    @Size(max = 40, message = "El estado civil no puede superar 40 caracteres")
    private String estadoCivil;

    @Size(max = 500, message = "La dirección no puede superar 500 caracteres")
    private String direccion;

    @NotBlank(message = "El parentesco es obligatorio")
    @Size(max = 30, message = "El parentesco no puede superar 30 caracteres")
    private String parentesco;

    @NotNull(message = "Indique si es el responsable principal")
    private Boolean esResponsablePrincipal;

    @NotNull(message = "Indique si está autorizado para retirar al estudiante")
    private Boolean autorizadoRetiro;

    @Size(max = 255, message = "Las observaciones no pueden superar 255 caracteres")
    private String observaciones;

    @Size(max = 2, message = "Se permiten como máximo dos contactos de emergencia.")
    private List<@Valid ContactoEmergenciaUpdateRequest> contactosEmergencia;
}