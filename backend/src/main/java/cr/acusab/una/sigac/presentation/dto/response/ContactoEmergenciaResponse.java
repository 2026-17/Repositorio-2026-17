package cr.acusab.una.sigac.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContactoEmergenciaResponse {

    private Long id;

    private String nombre;

    private String telefono;

    private String relacion;

    private Boolean activo;

    private Instant creadoEn;

    private Instant actualizadoEn;
}