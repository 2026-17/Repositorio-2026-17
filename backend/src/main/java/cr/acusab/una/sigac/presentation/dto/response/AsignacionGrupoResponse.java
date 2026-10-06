package cr.acusab.una.sigac.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AsignacionGrupoResponse {
    private Long idGrupo;
    private String codigoGrupo;
    private Long idCurso;
    private String codigoCurso;
    private String nombreCurso;
    private Long idPeriodo;
    private String codigoPeriodo;
    private Long idProfesor;
    private String nombreProfesor;
    private String estadoGrupo;
    private List<HorarioResponse> horarios;
    private String mensaje;
}
