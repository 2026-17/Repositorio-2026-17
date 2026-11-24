package cr.acusab.una.sigac.presentation.dto.response;

import java.util.List;

/*
 * Agrupaciones - Una agrupación del catálogo con sus instrumentos
 * permitidos (vacío = cualquier instrumento) y cuántos grupos ha tenido.
 */
public record AgrupacionResponse(
        Long id,
        String codigo,
        String nombre,
        String tipo,
        String descripcion,
        boolean activa,
        List<Instrumento> instrumentos,
        long cantidadGrupos,
        boolean soloInclusion,
        Integer edadMinima,
        Integer edadMaxima
) {

    public record Instrumento(Integer id, String nombre) {
    }
}
