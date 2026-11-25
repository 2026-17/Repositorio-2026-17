package cr.acusab.una.sigac.presentation.dto.response;

import java.util.List;

/*
 * Cursos a coordinar en Dirección: un grupo de un curso a coordinar del
 * período, con los estudiantes matriculados (detalles no retirados de
 * matrículas REGISTRADA) y lo que la Dirección anotó para cada uno.
 */
public record CursoACoordinarResponse(
        Long idGrupo,
        String codigo,
        Long idCurso,
        String codigoCurso,
        String nombreCurso,
        String estado,
        int cupoMaximo,
        int matriculados,
        List<Estudiante> estudiantes
) {

    public record Estudiante(
            Long idDetalle,
            Long idEstudiante,
            String cedula,
            String nombreCompleto,
            String instrumento,
            Long idProfesorAsignado,
            String nombreProfesorAsignado,
            String notasCoordinacion
    ) {
    }
}
