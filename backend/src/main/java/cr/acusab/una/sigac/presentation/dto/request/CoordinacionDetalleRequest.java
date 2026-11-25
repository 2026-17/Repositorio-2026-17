package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/*
 * Cursos a coordinar en Dirección: lo que la Dirección Académica anota
 * para UN estudiante en un curso a coordinar.
 *
 * - idProfesorAsignado: profesor activo, o null para dejarlo sin asignar.
 * - notasCoordinacion: cómo lo cumplirá (en qué agrupación, a quién
 *   acompaña, con quiénes, acuerdos de horario), o null / vacío.
 */
public record CoordinacionDetalleRequest(
        @Positive(message = "El profesor seleccionado no es válido.")
        Long idProfesorAsignado,

        @Size(max = 500, message = "La nota de coordinación no puede superar los 500 caracteres.")
        String notasCoordinacion
) {
}
