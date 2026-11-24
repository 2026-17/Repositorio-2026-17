package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Instrumento;
import cr.acusab.una.sigac.data.entity.Profesor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/*
 * Especialidad del profesor: texto separado por comas, por ejemplo
 * "Violín, Viola". Cada parte se compara con el NOMBRE EXACTO del
 * instrumento (sin distinguir mayúsculas ni espacios de más), no con
 * "contiene": así "Viola" no coincide con "Violonchelo".
 *
 * La usan los espacios de "cualquier instrumento del profesor" para saber
 * qué instrumentos da y si da el de una lección.
 */
public final class EspecialidadProfesor {

    private EspecialidadProfesor() {
    }

    // "Violín, Viola" -> ["violín", "viola"]
    static List<String> partes(String especialidad) {
        if (especialidad == null || especialidad.isBlank()) {
            return List.of();
        }
        return Arrays.stream(especialidad.split(","))
                .map(EspecialidadProfesor::normalizar)
                .filter(parte -> !parte.isEmpty())
                .toList();
    }

    public static boolean da(Profesor profesor, Instrumento instrumento) {
        return profesor != null && instrumento != null && instrumento.getNombre() != null
                && partes(profesor.getEspecialidad()).contains(normalizar(instrumento.getNombre()));
    }

    /*
     * Instrumentos activos que da el profesor, en el orden de su
     * especialidad. "activos" es la lista de instrumentos activos.
     */
    public static List<Instrumento> instrumentosDe(Profesor profesor, Collection<Instrumento> activos) {
        if (profesor == null) {
            return List.of();
        }
        List<String> partes = partes(profesor.getEspecialidad());
        return activos.stream()
                .filter(i -> Boolean.TRUE.equals(i.getActivo()) && i.getNombre() != null)
                .filter(i -> partes.contains(normalizar(i.getNombre())))
                .sorted((a, b) -> Integer.compare(
                        partes.indexOf(normalizar(a.getNombre())),
                        partes.indexOf(normalizar(b.getNombre()))))
                .toList();
    }

    // ["Violín", "Viola"] -> "Violín o Viola"
    public static String unirNombres(Collection<Instrumento> instrumentos) {
        return instrumentos.stream().map(Instrumento::getNombre).collect(Collectors.joining(" o "));
    }

    private static String normalizar(String texto) {
        return texto.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
