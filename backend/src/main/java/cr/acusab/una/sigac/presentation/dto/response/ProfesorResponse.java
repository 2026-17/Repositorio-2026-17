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
public class ProfesorResponse {
    private Long id;
    private TipoIdentificacionResponse tipoIdentificacion;
    private String numeroIdentificacion;
    private String nombres;
    private String primerApellido;
    private String segundoApellido;
    private String telefono;
    private String correo;
    private String direccion;
    private UbicacionResponse ubicacion;
    private String gradoAcademico;
    private String especialidad;
    private String empleador;
    private String clasePuesto;
    private LocalDate fechaIngreso;
    private LocalDate fechaSalida;
    private Boolean activo;
    private Instant creadoEn;
    private Instant actualizadoEn;
}
