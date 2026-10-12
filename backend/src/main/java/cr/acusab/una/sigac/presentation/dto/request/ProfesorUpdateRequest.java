package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static cr.acusab.una.sigac.presentation.dto.request.PoliticaDatosPersona.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProfesorUpdateRequest {

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

    @Pattern(regexp = TELEFONO_OPCIONAL_REGEX, message = TELEFONO_MENSAJE)
    private String telefono;

    @Email(message = CORREO_MENSAJE)
    @Pattern(regexp = CORREO_OPCIONAL_REGEX, message = CORREO_MENSAJE)
    @Size(max = 254, message = "El correo no puede superar 254 caracteres")
    private String correo;

    // Opcional, igual que la direccion: provincia y canton se deducen del distrito
    private Integer codigoDistrito;

    @Size(max = 500, message = "La dirección no puede superar 500 caracteres")
    private String direccion;

    @Size(max = 120, message = "El grado académico no puede superar 120 caracteres")
    private String gradoAcademico;

    @Size(max = 180, message = "La especialidad no puede superar 180 caracteres")
    private String especialidad;

    @Size(max = 180, message = "El empleador no puede superar 180 caracteres")
    private String empleador;

    @Size(max = 255, message = "La clase de puesto no puede superar 255 caracteres")
    private String clasePuesto;

    @PastOrPresent(message = "La fecha de ingreso no puede ser futura.")
    private LocalDate fechaIngreso;
}
