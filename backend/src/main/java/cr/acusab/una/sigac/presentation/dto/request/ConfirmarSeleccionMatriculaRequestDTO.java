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
 *
 * Que venga al menos uno de los dos lo valida el servicio (puede haber
 * matrícula solo con lecciones de instrumento).
 */
@Getter
@Setter
public class ConfirmarSeleccionMatriculaRequestDTO {

    private List<Long> idsGrupo = new ArrayList<>();

    private List<Long> idsEspacio = new ArrayList<>();
}
