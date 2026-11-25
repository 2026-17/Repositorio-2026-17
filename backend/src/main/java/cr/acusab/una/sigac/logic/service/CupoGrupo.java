package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Curso;
import cr.acusab.una.sigac.data.entity.Grupo;

import java.util.Map;

/*
 * Cupo de los grupos.
 *
 * Los grupos de los cursos «a coordinar en Dirección» (Curso.aCoordinar,
 * como Música de Cámara I-VI) NO tienen cupo: nunca se llenan y en las
 * pantallas dicen «Sin cupo». Como grupos.cupo_maximo es NOT NULL con un
 * CHECK (> 0), se les guarda CUPO_SIN_LIMITE, que nunca se usa para contar.
 *
 * Los demás grupos (cursos normales, espacios de instrumento y agrupaciones)
 * tienen el cupo de siempre.
 */
public final class CupoGrupo {

    // Valor fijo que se guarda en cupo_maximo de un grupo sin cupo.
    public static final short CUPO_SIN_LIMITE = 999;

    private CupoGrupo() {
    }

    // ¿El grupo es de un curso a coordinar (tipo CURSO y curso.aCoordinar)?
    public static boolean esSinCupo(Grupo grupo, Curso curso) {
        return grupo != null
                && !grupo.esEspacioInstrumento()
                && !grupo.esAgrupacion()
                && curso != null
                && curso.esACoordinar();
    }

    // Igual, buscando el curso del grupo en un mapa ya cargado (sin consultas extra).
    public static boolean esSinCupo(Grupo grupo, Map<Long, Curso> cursosPorId) {
        if (grupo == null || grupo.getIdCurso() == null || cursosPorId == null) {
            return false;
        }
        return esSinCupo(grupo, cursosPorId.get(grupo.getIdCurso()));
    }

    // ¿El grupo ya no tiene cupos? Un grupo sin cupo nunca se llena.
    public static boolean estaLleno(Grupo grupo, Curso curso, long ocupados) {
        return !esSinCupo(grupo, curso) && ocupados >= grupo.getCupoMaximo();
    }
}
