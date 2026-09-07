package cr.acusab.una.sigac.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public record EstudianteCreateRequest(
        @NotNull(message = "El tipo de identificación es obligatorio")
        Integer idTipoIdentificacion,

        @NotBlank(message = "El número de identificación es obligatorio")
        @Size(max = 40, message = "El número de identificación no puede superar 40 caracteres")
        String numeroIdentificacion,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String nombres,

        @NotBlank(message = "El primer apellido es obligatorio")
        @Size(max = 80, message = "El primer apellido no puede superar 80 caracteres")
        String primerApellido,

        @Size(max = 80, message = "El segundo apellido no puede superar 80 caracteres")
        String segundoApellido,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @PastOrPresent(message = "La fecha de nacimiento no puede ser futura")
        LocalDate fechaNacimiento,

        @NotBlank(message = "El género es obligatorio")
        @Size(max = 30, message = "El género no puede superar 30 caracteres")
        String genero,

        @Size(max = 30, message = "El teléfono no puede superar 30 caracteres")
        String telefono,

        @Email(message = "El correo electrónico no tiene un formato válido")
        @Size(max = 254, message = "El correo no puede superar 254 caracteres")
        String correo,

        @Size(max = 500, message = "La dirección no puede superar 500 caracteres")
        String direccion,

        @Size(max = 80, message = "El nivel escolar no puede superar 80 caracteres")
        String nivelEscolar,

        Long idCentroEducativo,

        String condicionesRelevantes,

        @NotNull(message = "El instrumento es obligatorio")
        Integer idInstrumento,

        @NotEmpty(message = "Debe indicar al menos un encargado para un menor")
        @Valid
        List<EncargadoRequest> encargados
) {
    public record EncargadoRequest(
            Integer idTipoIdentificacion,

            @Size(max = 40, message = "La identificación del encargado no puede superar 40 caracteres")
            String numeroIdentificacion,

            @NotBlank(message = "El nombre del encargado es obligatorio")
            @Size(max = 100, message = "El nombre del encargado no puede superar 100 caracteres")
            String nombres,

            @NotBlank(message = "El primer apellido del encargado es obligatorio")
            @Size(max = 80, message = "El primer apellido del encargado no puede superar 80 caracteres")
            String primerApellido,

            @Size(max = 80, message = "El segundo apellido del encargado no puede superar 80 caracteres")
            String segundoApellido,

            @NotBlank(message = "El teléfono principal del encargado es obligatorio")
            @Size(max = 30, message = "El teléfono no puede superar 30 caracteres")
            String telefonoPrincipal,

            @Size(max = 30, message = "El teléfono secundario no puede superar 30 caracteres")
            String telefonoSecundario,

            @Email(message = "El correo del encargado no tiene un formato válido")
            @Size(max = 254, message = "El correo no puede superar 254 caracteres")
            String correo,

            @Size(max = 120, message = "La ocupación no puede superar 120 caracteres")
            String ocupacion,

            @NotBlank(message = "El parentesco es obligatorio")
            String parentesco,

            boolean responsablePrincipal,
            boolean contactoEmergencia
    ) {}
}
