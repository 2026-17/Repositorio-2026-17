package cr.acusab.una.sigac.logic.service;

import cr.acusab.una.sigac.data.entity.Agrupacion;
import cr.acusab.una.sigac.data.entity.Curso;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/*
 * Familias de cursos que se aprueban en las agrupaciones.
 *
 * Cada familia es una lista ORDENADA de códigos de curso: la posición es el
 * número del nivel (el primero es el nivel I). Para cambiar los códigos o
 * agregar niveles basta con editar estas listas; el último código es el
 * nivel máximo (regla D3).
 */
public enum FamiliaAgrupacion {

    CONJUNTO_INSTRUMENTAL("Conjunto Instrumental", "CI", List.of(
            "CI-I", "CI-II", "CI-III", "CI-IV", "CI-V", "CI-VI",
            "CI-VII", "CI-VIII", "CI-IX", "CI-X", "CI-XI", "CI-XII")),

    CORO("Coro", "Coro", List.of(
            "CORO-1", "CORO-II", "CORO-III", "CORO-IV", "CORO-V", "CORO-VI", "CORO-VII",
            "CORO-VIII", "CORO-IX", "CORO-X", "CORO-XI", "CORO-XII", "CORO-XIII", "CORO-XIV"));

    private static final String[] ROMANOS = {
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
            "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"
    };

    private final String nombre;
    private final String abreviatura;
    private final List<String> codigos;

    FamiliaAgrupacion(String nombre, String abreviatura, List<String> codigos) {
        this.nombre = nombre;
        this.abreviatura = abreviatura;
        this.codigos = codigos;
    }

    public String nombre() {
        return nombre;
    }

    public String abreviatura() {
        return abreviatura;
    }

    public List<String> codigos() {
        return codigos;
    }

    public int nivelMaximo() {
        return codigos.size();
    }

    // Número del nivel (1 = I) según el código del curso; 0 si no es de la familia.
    public int numero(String codigo) {
        if (codigo == null) {
            return 0;
        }
        return codigos.indexOf(codigo.trim().toUpperCase(Locale.ROOT)) + 1;
    }

    public boolean incluye(Curso curso) {
        return curso != null && numero(curso.getCodigo()) > 0;
    }

    // INSTRUMENTAL cuenta para Conjunto Instrumental; CORAL, para Coro.
    public static FamiliaAgrupacion de(Agrupacion agrupacion) {
        return agrupacion.esCoral() ? CORO : CONJUNTO_INSTRUMENTAL;
    }

    public static Optional<FamiliaAgrupacion> deCurso(Curso curso) {
        for (FamiliaAgrupacion familia : values()) {
            if (familia.incluye(curso)) {
                return Optional.of(familia);
            }
        }
        return Optional.empty();
    }

    /*
     * ¿El código es de un curso que se cursa en agrupaciones? Es la misma
     * regla con que el script 2026-11-24_agrupaciones_ajustes.sql marca
     * cursos.es_curso_agrupacion (CI-% y CORO-%).
     */
    public static boolean esCodigoDeAgrupacion(String codigo) {
        if (codigo == null) {
            return false;
        }
        String c = codigo.trim().toUpperCase(Locale.ROOT);
        return c.startsWith("CI-") || c.startsWith("CORO-");
    }

    // 5 -> "V"
    public static String romano(int numero) {
        return numero >= 1 && numero <= ROMANOS.length ? ROMANOS[numero - 1] : String.valueOf(numero);
    }
}
