package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;
import java.util.List;

/*
 * Todo lo que se guarda desde la pantalla "Editar estudiante". Reutiliza
 * EstudianteUpdateRequest y EncargadoUpdateRequest para no repetir sus
 * validaciones. Los encargados solo se toman en cuenta si es menor de edad.
 */
public record EstudianteEdicionCompletaRequest(
        @NotNull(message = "Faltan los datos del estudiante.")
        @Valid
        EstudianteUpdateRequest estudiante,

        @NotNull(message = "La fecha de ingreso es obligatoria.")
        @PastOrPresent(message = "La fecha de ingreso no puede ser futura.")
        LocalDate fechaIngreso,

        // Profesor del instrumento principal; null = sin profesor.
        Long idProfesor,

        List<@Valid EncargadoEdicion> encargados
) {

    // idEncargado null = encargado nuevo. datos incluye parentesco, permisos y contactos.
    public record EncargadoEdicion(
            Long idEncargado,

            @NotNull(message = "Faltan los datos del encargado.")
            @Valid
            EncargadoUpdateRequest datos
    ) {}
}
