package cr.acusab.una.sigac.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GrupoResponse {
    private Long idGrupo;
    private String codigo;
    private Long idCurso;
    private Long idPeriodo;
    private Long idProfesor;
    private Short cupoMaximo;
    private String modalidad;
    private String estado;
}
