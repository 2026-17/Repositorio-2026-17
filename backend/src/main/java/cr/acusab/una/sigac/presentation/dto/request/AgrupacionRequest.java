package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/*
 * Agrupaciones - Crear o modificar una agrupación del catálogo.
 * idsInstrumento: instrumentos permitidos (vacío = cualquier instrumento).
 */
@Getter
@Setter
public class AgrupacionRequest {

    @NotBlank(message = "El código es obligatorio.")
    @Size(max = 20, message = "El código no puede superar los 20 caracteres.")
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "El código solo puede tener letras sin tilde, números y guiones.")
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio.")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres.")
    private String nombre;

    @NotBlank(message = "El tipo es obligatorio.")
    @Pattern(regexp = "INSTRUMENTAL|CORAL", message = "El tipo debe ser Instrumental o Coral.")
    private String tipo;

    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres.")
    private String descripcion;

    // Si no se envía, la agrupación queda activa.
    private Boolean activa;

    private List<Integer> idsInstrumento = new ArrayList<>();

    // ¿Quiénes pueden matricularla? (opcional; D6 y D7)
    private Boolean soloInclusion;

    @Min(value = 0, message = "La edad mínima no es válida.")
    @Max(value = 120, message = "La edad mínima no es válida.")
    private Integer edadMinima;

    @Min(value = 0, message = "La edad máxima no es válida.")
    @Max(value = 120, message = "La edad máxima no es válida.")
    private Integer edadMaxima;
}
