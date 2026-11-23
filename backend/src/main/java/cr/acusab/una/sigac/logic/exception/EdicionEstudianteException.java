package cr.acusab.una.sigac.logic.exception;

import jakarta.validation.ConstraintViolation;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Edición completa del estudiante: el guardado es todo o nada. Esta excepción
 * revierte la transacción y le dice a la pantalla qué revisar en cada sección.
 */
public class EdicionEstudianteException extends BusinessException {

    public static final String PERSONAL = "personal";
    public static final String CONTACTO = "contacto";
    public static final String ACADEMICO = "academico";
    public static final String INSTRUMENTOS = "instrumentos";
    public static final String ENCARGADOS = "encargados";
    public static final String POLIZA = "poliza";

    private static final List<String> ORDEN_SECCIONES =
            List.of(PERSONAL, CONTACTO, ACADEMICO, INSTRUMENTOS, ENCARGADOS, POLIZA);

    private static final Set<String> CAMPOS_CONTACTO =
            Set.of("correo", "telefono", "codigoDistrito", "direccion");
    private static final Set<String> CAMPOS_ACADEMICOS =
            Set.of("nivelEscolar", "centroEducativo", "idsPlanEstudio", "fechaIngreso");
    private static final Set<String> CAMPOS_INSTRUMENTOS = Set.of("idInstrumento", "idProfesor");

    private static final Pattern INDICE_ENCARGADO = Pattern.compile("^encargados\\[(\\d+)]");

    // indiceEncargado: posición en la lista enviada; null si no es de un encargado.
    public record ErrorEdicion(String seccion, String mensaje, Integer indiceEncargado) {
        public ErrorEdicion(String seccion, String mensaje) {
            this(seccion, mensaje, null);
        }
    }

    private final List<ErrorEdicion> errores;

    public EdicionEstudianteException(List<ErrorEdicion> errores) {
        super("No se guardó: revise lo siguiente.");
        this.errores = errores.stream()
                .sorted(Comparator.comparingInt(error -> ORDEN_SECCIONES.indexOf(error.seccion())))
                .toList();
    }

    public List<ErrorEdicion> getErrores() {
        return errores;
    }

    // Convierte los errores de las anotaciones del DTO a errores por sección.
    public static <T> List<ErrorEdicion> desdeViolaciones(Set<ConstraintViolation<T>> violaciones) {
        return violaciones.stream()
                .map(violacion -> aError(violacion.getPropertyPath().toString(), violacion.getMessage()))
                .distinct()
                .sorted(Comparator.comparing(ErrorEdicion::mensaje))
                .toList();
    }

    private static ErrorEdicion aError(String ruta, String mensaje) {
        Matcher encargado = INDICE_ENCARGADO.matcher(ruta);
        if (encargado.find()) {
            return new ErrorEdicion(ENCARGADOS, mensaje, Integer.valueOf(encargado.group(1)));
        }
        if (ruta.startsWith("encargados")) {
            return new ErrorEdicion(ENCARGADOS, mensaje);
        }
        String campo = ruta.substring(ruta.lastIndexOf('.') + 1).replaceAll("\\[.*]", "");
        if (CAMPOS_CONTACTO.contains(campo)) return new ErrorEdicion(CONTACTO, mensaje);
        if (CAMPOS_ACADEMICOS.contains(campo)) return new ErrorEdicion(ACADEMICO, mensaje);
        if (CAMPOS_INSTRUMENTOS.contains(campo)) return new ErrorEdicion(INSTRUMENTOS, mensaje);
        return new ErrorEdicion(PERSONAL, mensaje);
    }
}
