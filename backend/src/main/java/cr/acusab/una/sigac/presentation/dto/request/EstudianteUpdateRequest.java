package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static cr.acusab.una.sigac.presentation.dto.request.PoliticaDatosPersona.*;

import java.time.LocalDate;
import java.util.List;

public record EstudianteUpdateRequest(
        @NotNull(message = "El tipo de identificación es obligatorio")
        Integer idTipoIdentificacion,

        @NotBlank(message = "El número de identificación es obligatorio")
        @Size(max = 40, message = "El número de identificación no puede superar 40 caracteres")
        String numeroIdentificacion,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        @Pattern(regexp = NOMBRE_REGEX, message = NOMBRE_MENSAJE)
        String nombres,

        @NotBlank(message = "El primer apellido es obligatorio")
        @Size(max = 80, message = "El primer apellido no puede superar 80 caracteres")
        @Pattern(regexp = NOMBRE_REGEX, message = NOMBRE_MENSAJE)
        String primerApellido,

        @Size(max = 80, message = "El segundo apellido no puede superar 80 caracteres")
        @Pattern(regexp = NOMBRE_OPCIONAL_REGEX, message = NOMBRE_MENSAJE)
        String segundoApellido,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @PastOrPresent(message = "La fecha de nacimiento no puede ser futura")
        LocalDate fechaNacimiento,

        @NotBlank(message = "El género es obligatorio")
        @Size(max = 30, message = "El género no puede superar 30 caracteres")
        String genero,

        @NotNull(message = "El distrito es obligatorio")
        Integer codigoDistrito,

        @NotBlank(message = "La dirección exacta es obligatoria")
        @Size(max = 500, message = "La dirección no puede superar 500 caracteres")
        String direccion,

        @Pattern(regexp = TELEFONO_OPCIONAL_REGEX, message = TELEFONO_MENSAJE)
        String telefono,

        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = CORREO_MENSAJE)
        @Pattern(regexp = CORREO_REGEX, message = CORREO_MENSAJE)
        @Size(max = 254, message = "El correo no puede superar 254 caracteres")
        String correo,

        @NotBlank(message = "El nivel escolar es obligatorio")
        @Size(max = 80, message = "El nivel escolar no puede superar 80 caracteres")
        String nivelEscolar,

        @Size(max = 180, message = "El centro educativo no puede superar 180 caracteres")
        String centroEducativo,

        @Size(max = 2000, message = "Las condiciones relevantes no pueden superar 2000 caracteres")
        String condicionesRelevantes,

        @NotNull(message = "El instrumento es obligatorio")
        Integer idInstrumento,

        @NotEmpty(message = "Debe seleccionar al menos un plan de estudio") List<Long> idsPlanEstudio
) {}
