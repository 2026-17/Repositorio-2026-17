package cr.acusab.una.sigac.logic.service;

import java.time.LocalDate;
import java.time.Period;

/*
 * Regla D7: agrupaciones y cursos pueden tener edad mínima y máxima
 * opcionales, en años cumplidos a la fecha de inicio del período. Los
 * límites se editan en pantalla; aquí solo está cómo se comparan y los
 * mensajes.
 */
public final class ReglasEdad {

    private ReglasEdad() {
    }

    // Años cumplidos a una fecha (null si falta alguna de las dos).
    public static Integer edadAl(LocalDate nacimiento, LocalDate fecha) {
        if (nacimiento == null || fecha == null) {
            return null;
        }
        return Period.between(nacimiento, fecha).getYears();
    }

    /*
     * Motivo del bloqueo, o null si la edad está dentro del rango (o si no
     * hay límites, o no se conoce la edad del estudiante).
     */
    public static String motivoBloqueo(Integer edadMinima, Integer edadMaxima, Integer edad) {
        if (edad == null || (edadMinima == null && edadMaxima == null)) {
            return null;
        }
        boolean fuera = (edadMinima != null && edad < edadMinima) || (edadMaxima != null && edad > edadMaxima);
        return fuera ? descripcion(edadMinima, edadMaxima) : null;
    }

    // "Es solo para estudiantes de 6 a 12 años", "... menores de 6 años", "... de 15 años o más".
    public static String descripcion(Integer edadMinima, Integer edadMaxima) {
        if (edadMinima != null && edadMaxima != null) {
            return "Es solo para estudiantes de " + edadMinima + " a " + edadMaxima + " años.";
        }
        if (edadMaxima != null) {
            return "Es solo para menores de " + (edadMaxima + 1) + " años.";
        }
        if (edadMinima != null) {
            return "Es solo para estudiantes de " + edadMinima + " años o más.";
        }
        return null;
    }

    // Validación de los límites que se escriben en pantalla (null = válidos).
    public static String errorDeLimites(Integer edadMinima, Integer edadMaxima) {
        if ((edadMinima != null && (edadMinima < 0 || edadMinima > 120))
                || (edadMaxima != null && (edadMaxima < 0 || edadMaxima > 120))) {
            return "Las edades deben estar entre 0 y 120 años.";
        }
        if (edadMinima != null && edadMaxima != null && edadMaxima < edadMinima) {
            return "La edad máxima no puede ser menor que la mínima.";
        }
        return null;
    }
}
