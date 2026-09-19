package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ConfiguracionRequisitosCursoRequest {
    @NotNull
    private List<@NotNull @Positive Long> prerrequisitos = new ArrayList<>();
    @NotNull
    private List<@NotNull @Positive Long> correquisitos = new ArrayList<>();
    @Min(0) @Max(150)
    private Integer edadMinima;
    @Min(0) @Max(150)
    private Integer edadMaxima;
}
