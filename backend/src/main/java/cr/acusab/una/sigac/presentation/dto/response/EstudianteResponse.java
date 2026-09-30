package cr.acusab.una.sigac.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EstudianteResponse {
    private Long id;
    private TipoIdentificacionResponse tipoIdentificacion;
    private String numeroIdentificacion;
    private String nombres;
    private String primerApellido;
    private String segundoApellido;
    private LocalDate fechaNacimiento;
    private String genero;
    private String telefono;
    private String correo;
    private String direccion;
    private String nivelEscolar;
    private Integer idCentroEducativo;
    private String condicionesRelevantes;
    private LocalDate fechaIngreso;
    private LocalDate fechaEgreso;
    private Boolean activo;
    private Instant creadoEn;
    private Instant actualizadoEn;
}
