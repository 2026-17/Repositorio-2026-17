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
public class GrupoDeProfesorResponse {
    private Long idGrupo;
    private String codigoGrupo;
    private Long idCurso;
    private String codigoCurso;
    private String nombreCurso;
    private Long idPeriodo;
    private String codigoPeriodo;
    private String estadoGrupo;
    private List<HorarioResponse> horarios;
}
