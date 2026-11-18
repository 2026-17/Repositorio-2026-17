package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/*
 * Crear un programa académico. No se liga a ningún período: un programa
 * es la "carrera" (ej. Formación Musical Infantil) que agrupa planes de
 * estudio y cursos.
 *
 * planInicial es opcional: si viene, el programa nace con su primer plan
 * de estudio vigente y ya se le pueden crear cursos.
 */
@Getter
@Setter
public class ProgramaCreateRequest {

    @NotBlank(message = "El código del programa es obligatorio.")
    @Size(max = 30, message = "El código del programa no puede superar los 30 caracteres.")
    @Pattern(
            regexp = "^[A-Za-z0-9]+(-[A-Za-z0-9]+)*$",
            message = "El código solo puede tener letras, números y guiones (ej. FMI-2027)."
    )
    private String codigo;

    @NotBlank(message = "El nombre del programa es obligatorio.")
    @Size(max = 150, message = "El nombre del programa no puede superar los 150 caracteres.")
    private String nombre;

    @Size(max = 2000, message = "La descripción no puede superar los 2000 caracteres.")
    private String descripcion;

    @Valid
    private PlanEstudioInicialRequest planInicial;
}
