package cr.acusab.una.sigac.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EstudianteUpdateRequest {

    @NotNull
    private Integer idTipoIdentificacion;

    @NotBlank
    @Size(max = 40)
    private String numeroIdentificacion;

    @NotBlank
    @Size(max = 100)
    private String nombres;

    @NotBlank
    @Size(max = 80)
    private String primerApellido;

    @Size(max = 80)
    private String segundoApellido;

    private LocalDate fechaNacimiento;

    @Size(max = 30)
    private String genero;

    @Size(max = 30)
    private String telefono;

    @Email
    @Size(max = 254)
    private String correo;

    @Size(max = 500)
    private String direccion;

    @Size(max = 80)
    private String nivelEscolar;

    private Integer idCentroEducativo;

    private String condicionesRelevantes;

    private LocalDate fechaIngreso;

    private LocalDate fechaEgreso;
}
