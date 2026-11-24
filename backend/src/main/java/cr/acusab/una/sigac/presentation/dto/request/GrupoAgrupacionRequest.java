package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/*
 * Agrupaciones - Crear o modificar el grupo de una agrupación en un
 * período (mockup 9 · Director: crear agrupación).
 *
 * - estado: PLANIFICADO (guardar como planificada) o PUBLICADO (guardar y
 *   publicar; exige un profesor responsable activo).
 * - profesores: uno de ellos con responsable = true.
 * - idsInstrumento: "Solo para estudiantes de". Si viene (aunque sea
 *   vacía), reemplaza los instrumentos permitidos de la agrupación (son del
 *   catálogo y valen para todos sus períodos). Si es null, no se tocan.
 */
@Getter
@Setter
public class GrupoAgrupacionRequest {

    @NotNull(message = "La agrupación es obligatoria.")
    @Positive(message = "La agrupación seleccionada no es válida.")
    private Long idAgrupacion;

    @NotNull(message = "El período académico es obligatorio.")
    @Positive(message = "El período académico seleccionado no es válido.")
    private Long idPeriodo;

    @NotNull(message = "El cupo es obligatorio.")
    @Min(value = 1, message = "El cupo debe ser de al menos 1 estudiante.")
    @Max(value = 500, message = "El cupo no puede superar los 500 estudiantes.")
    private Integer cupoMaximo;

    @Size(max = 80, message = "El aula no puede superar los 80 caracteres.")
    private String aula;

    @NotNull(message = "Indique si se guarda como planificada o se publica.")
    @Pattern(regexp = "PLANIFICADO|PUBLICADO", message = "El estado debe ser planificado o publicado.")
    private String estado;

    @Valid
    private List<HorarioGrupoRequestDTO> bloques = new ArrayList<>();

    @Valid
    private List<ProfesorGrupo> profesores = new ArrayList<>();

    private List<Integer> idsInstrumento;

    /*
     * "¿Quiénes pueden matricularla?". Igual que idsInstrumento, es de la
     * agrupación (catálogo): si viene, reemplaza esos datos; si es null, no
     * se tocan.
     */
    @Valid
    private QuienesPueden quienesPueden;

    @Getter
    @Setter
    public static class QuienesPueden {

        private boolean soloInclusion;

        @Min(value = 0, message = "La edad mínima no es válida.")
        @Max(value = 120, message = "La edad mínima no es válida.")
        private Integer edadMinima;

        @Min(value = 0, message = "La edad máxima no es válida.")
        @Max(value = 120, message = "La edad máxima no es válida.")
        private Integer edadMaxima;
    }

    @Getter
    @Setter
    public static class ProfesorGrupo {

        @NotNull(message = "El profesor es obligatorio.")
        @Positive(message = "El profesor seleccionado no es válido.")
        private Long idProfesor;

        private boolean responsable;
    }
}
