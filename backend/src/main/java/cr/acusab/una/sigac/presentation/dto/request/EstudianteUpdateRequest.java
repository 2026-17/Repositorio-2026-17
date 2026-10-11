package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record EstudianteUpdateRequest(
        @NotNull Integer idTipoIdentificacion,
        @NotBlank @Size(max = 40) String numeroIdentificacion,
        @NotBlank @Size(max = 100) String nombres,
        @NotBlank @Size(max = 80) String primerApellido,
        @Size(max = 80) String segundoApellido,
        @NotNull @PastOrPresent LocalDate fechaNacimiento,
        @NotBlank @Size(max = 30) String genero,
        @NotNull(message = "El distrito es obligatorio") Integer codigoDistrito,
        @NotBlank @Size(max = 500) String direccion,
        @Size(max = 30) String telefono,
        @NotBlank @Email @Size(max = 254) String correo,
        @NotBlank @Size(max = 80) String nivelEscolar,
        @Size(max = 180) String centroEducativo,
        String condicionesRelevantes,
        @NotNull Integer idInstrumento,
        @NotEmpty(message = "Debe seleccionar al menos un plan de estudio") List<Long> idsPlanEstudio
) {}
