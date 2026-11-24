package cr.acusab.una.sigac.presentation.dto.response;

/*
 * Espacios de instrumento - Un instrumento activo que da un profesor
 * (según su especialidad). La pantalla "Crear espacios" ofrece
 * "Cualquiera de sus instrumentos" cuando hay dos o más.
 */
public record InstrumentoProfesorResponse(
        Integer id,
        String nombre
) {
}
