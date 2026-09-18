package cr.acusab.una.sigac.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EncargadoResponse {

    private Long id;

    private Long idEstudiante;

    private TipoIdentificacionResponse tipoIdentificacion;

    private String numeroIdentificacion;

    private String nombres;

    private String primerApellido;

    private String segundoApellido;

    private String telefonoPrincipal;

    private String telefonoSecundario;

    private String correo;

    private String ocupacion;

    private String estadoCivil;

    private String direccion;

    private String parentesco;

    private Boolean esResponsablePrincipal;

    private Boolean autorizadoRetiro;

    private String observaciones;

    private Boolean activo;

    private Instant creadoEn;

    private Instant actualizadoEn;

    private List<ContactoEmergenciaResponse> contactosEmergencia;
}