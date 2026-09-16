package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Datos requeridos para registrar un encargado y asociarlo
 * a un estudiante en la HU-023 Registrar encargados.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EncargadoCreateRequest {

    @NotNull
    private Long idEstudiante;

    @NotNull
    private Integer idTipoIdentificacion;

    @NotBlank
    @Size(max = 40)
    private String numeroIdentificacion;

    @NotBlank
    @Size(max = 100)
    private String nombres;

    @NotBlank
    @Size(max = 80)
    private String primerApellido;

    @Size(max = 80)
    private String segundoApellido;

    @NotBlank
    @Size(max = 30)
    private String telefonoPrincipal;

    @Size(max = 30)
    private String telefonoSecundario;

    @Email
    @Size(max = 254)
    private String correo;

    @Size(max = 120)
    private String ocupacion;

    @Size(max = 40)
    private String estadoCivil;

    @Size(max = 500)
    private String direccion;

    @NotBlank
    @Size(max = 30)
    private String parentesco;

    @NotNull
    private Boolean esResponsablePrincipal;

    @NotNull
    private Boolean autorizadoRetiro;

    @Size(max = 255)
    private String observaciones;

    @Size(max = 2, message = "Se permiten como máximo dos contactos de emergencia.")
    private List<@Valid ContactoEmergenciaRequest> contactosEmergencia;
}