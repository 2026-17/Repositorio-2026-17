package cr.acusab.una.sigac.presentation.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/*
 * Historia 2 - Confirmación de matrícula: detalle de los cursos que
 * quedaron matriculados, con horarios, profesores y cupos ya
 * actualizados.
 */
@Getter
@Setter
public class MatriculaConfirmadaResponseDTO {

    private Long idMatricula;
    private String estado;
    private Instant fechaMatricula;
    private OfertaMatriculaResponseDTO.PeriodoDTO periodo;
    private List<OfertaMatriculaResponseDTO.GrupoDTO> cursos = new ArrayList<>();
}
