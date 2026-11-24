package cr.acusab.una.sigac.presentation.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/*
 * Historia 2 - Grupos que el estudiante confirma matricular.
 *
 * El estudiante y el período no se envían: se obtienen de la sesión
 * activa y del período académico vigente.
 *
 * idsGrupo: grupos normales elegidos.
 * idsEspacio: un espacio de instrumento por cada instrumento vigente. El
 * nivel no se envía: lo calcula el servidor.
 * espacios: igual que idsEspacio, pero indicando la lección (instrumento)
 * para la que se eligió cada espacio. Es necesario en un espacio de
 * "cualquier instrumento del profesor" cuando el estudiante tiene dos
 * lecciones con ese profesor; si tiene una sola, se deduce. Se puede
 * usar uno, el otro o ambos (sin repetir espacios).
 *
 * Que venga al menos un grupo o espacio lo valida el servicio (puede haber
 * matrícula solo con lecciones de instrumento).
 */
@Getter
@Setter
public class ConfirmarSeleccionMatriculaRequestDTO {

    private List<Long> idsGrupo = new ArrayList<>();

    private List<Long> idsEspacio = new ArrayList<>();

    private List<EspacioElegido> espacios = new ArrayList<>();

    @Getter
    @Setter
    public static class EspacioElegido {

        private Long idEspacio;

        // Instrumento de la lección para la que se eligió el espacio.
        private Integer idInstrumento;
    }
}
