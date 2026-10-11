package cr.acusab.una.sigac.presentation.dto.response;

import cr.acusab.una.sigac.data.entity.Distrito;

// Provincia, cantón y distrito de una persona. Los códigos permiten
// precargar los selectores del formulario al editar.
public record UbicacionResponse(
        Integer idProvincia,
        String provincia,
        Integer codigoCanton,
        String canton,
        Integer codigoDistrito,
        String distrito
) {
    public static UbicacionResponse de(Distrito d) {
        if (d == null) return null;
        var c = d.getCanton();
        var p = c.getProvincia();
        return new UbicacionResponse(p.getId(), p.getNombre(), c.getCodigo(), c.getNombre(), d.getCodigo(), d.getNombre());
    }
}
